package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is meant to be built as a stand-alone utility for use in integration tests.  It is a utility for various
 * interactions with WebSockets.
 * Args:
 * [0] - XSRF token
 * [1] - SEND/JSON_IO
 * [2] - URI
 * [3] - protocol
 * [[4] - [JSON_IO input_pipe]]
 * [[5] - JSON_IO output_pipe]
 * 
 * In "SEND" mode, will read stdin, sending this data to the other side as binary.  Closes the socket on EOF.
 * In "JSON_IO" mode, we open an optional input_pipe and required output_pipe.  These don't need to be pipes, but the
 * design intends for this to be the case since we will always open-read/write-close for each message:
 * -open input pipe, read all bytes to EOF, send this as a String message, loop (0-byte message means "client-side
 * close")
 * -wait for message, open output pipie, write message, close, loop, exit when socket closes
 * NOTE:  If the input pipe is provided, a zero-byte message will ALWAYS be required in order to close down correctly.
 * Returns 0 on success, 1 on arg failure, 2 on WebSocket error.
 */
public class WebSocketUtility implements WebSocketListener
{
	private final File _outputPipe;
	private final WebSocketClient _client;
	private boolean _isRunning;
	private Session _connectedSession;
	private boolean _errorWasObserved;
	private Thread _inputReaderThread;


	public WebSocketUtility(File outputPipe)
	{
		_outputPipe = outputPipe;
		_client = new WebSocketClient();
	}

	public void connect(String uri, String xsrf, String protocol) throws Exception
	{
		_client.start();
		ClientUpgradeRequest req = new ClientUpgradeRequest();
		req.setCookies(Collections.singletonList(new HttpCookie("XSRF", xsrf)));
		req.setSubProtocols(protocol);
		_client.connect(this, new URI(uri), req);
		_isRunning = true;
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason)
	{
		WebSocketListener.super.onWebSocketClose(statusCode, reason);
		synchronized (this)
		{
			_isRunning = false;
			this.notifyAll();
		}
	}

	@Override
	public void onWebSocketConnect(Session session)
	{
		WebSocketListener.super.onWebSocketConnect(session);
		synchronized (this)
		{
			_connectedSession = session;
			this.notifyAll();
		}
	}

	@Override
	public void onWebSocketError(Throwable cause)
	{
		WebSocketListener.super.onWebSocketError(cause);
		_errorWasObserved = true;
	}

	@Override
	public void onWebSocketText(String message)
	{
		if (null != _outputPipe)
		{
			try
			{
				Files.write(_outputPipe.toPath(), message.getBytes(), StandardOpenOption.APPEND);
			}
			catch (IOException e)
			{
				throw Assert.unexpected(e);
			}
		}
	}

	public boolean waitForClose() throws Exception
	{
		synchronized (this)
		{
			while (_isRunning)
			{
				this.wait();
			}
		}
		_client.stop();
		if (null != _inputReaderThread)
		{
			_inputReaderThread.join();
		}
		return _errorWasObserved;
	}

	public void sendAllBinary(InputStream in) throws Exception
	{
		if (null != _connectedSession)
		{
			RemoteEndpoint endpoint = _connectedSession.getRemote();
			byte[] buffer = new byte[64];
			int read = in.read(buffer);
			while (read > 0)
			{
				endpoint.sendBytes(ByteBuffer.wrap(buffer, 0, read));
				// Recreate the buffer in case that wrapper is used asynchronously (probably not, but this is easy).
				buffer = new byte[64];
				read = in.read(buffer);
			}
			_connectedSession.close();
		}
	}

	public synchronized void waitForConnection() throws InterruptedException
	{
		while (_isRunning && (null == _connectedSession))
		{
			this.wait();
		}
	}

	public synchronized void startInputReaderThread(File inputPipe)
	{
		// We will handle each full extent of data read from the input pipe as a single message.
		// We will interpret a zero-byte message as an explicit client-side disconnect.
		if ((null != _connectedSession) && (null != inputPipe))
		{
			_inputReaderThread = new Thread(() -> {
				boolean shouldContinue = true;
				while (shouldContinue)
				{
					try
					{
						byte[] data = Files.readAllBytes(inputPipe.toPath());
						if (0 == data.length)
						{
							// Explicit close.
							System.err.println("Closing web socket");
							_connectedSession.close();
							shouldContinue = false;
						}
						else
						{
							// In this mode, we always send the data as a string since we assume it is JSON.
							_connectedSession.getRemote().sendString(new String(data));
						}
					}
					catch (IOException e)
					{
						// If something went wrong, we will also just exit.
						e.printStackTrace();
						shouldContinue = false;
					}
					
				}
			});
			_inputReaderThread.start();
		}
	}


	public static void main(String[] args) throws Exception
	{
		if (args.length < 4)
		{
			_usageExit();
		}
		else
		{
			String xsrf = args[0];
			String mode = args[1];
			String uri = args[2];
			String protocol = args[3];
			boolean sendMode = false;
			File inputPipe = null;
			File outputPipe = null;
			if (("SEND".equals(mode)) && (4 == args.length))
			{
				sendMode = true;
			}
			else if (("JSON_IO".equals(mode)) && ((5 == args.length) || (6 == args.length)))
			{
				int outputPipeIndex = 4;
				if (6 == args.length)
				{
					inputPipe = new File(args[4]);
					Assert.assertTrue(inputPipe.exists());
					outputPipeIndex += 1;
				}
				outputPipe = new File(args[outputPipeIndex]);
				Assert.assertTrue(outputPipe.exists());
			}
			else
			{
				_usageExit();
			}
			
			WebSocketUtility utility = new WebSocketUtility(outputPipe);
			utility.connect(uri, xsrf, protocol);
			utility.waitForConnection();
			if (sendMode)
			{
				utility.sendAllBinary(System.in);
			}
			else
			{
				// We are in interactive JSON_IO mode, which is more complicated:
				// -WebSocketUtility will write each message to the outputPipe, as a distinct open/close operation.
				// -we will need to wait for connection, then start a thread which reads from the input pipe and writes
				//  to the stream, as a distinct input file open/close operation
				utility.startInputReaderThread(inputPipe);
			}
			boolean wasSuccess = utility.waitForClose();
			if (wasSuccess)
			{
				System.exit(0);
			}
			else
			{
				System.exit(2);
			}
		}
	}


	private static void _usageExit()
	{
		System.err.println("Usage: XSRF_TOKEN SEND/JSON_IO URI protocol [[input_pipe] output_pipe]");
		System.exit(1);
	}
}
