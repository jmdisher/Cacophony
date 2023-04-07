package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * This is meant to be built as a stand-alone utility for use in integration tests.  It is a utility for various
 * interactions with WebSockets.
 * Args:
 * [0] - XSRF token
 * [1] - SEND/DRAIN/JSON_IO
 * [2] - URI
 * [3] - protocol
 * [[4] - JSON_IO output_pipe]
 * [[5] - [JSON_IO input_pipe]]
 * 
 * In "SEND" mode, will read stdin, sending this data to the other side as binary.  Closes the socket on EOF.
 * In "DRAIN" mode, will listen to the WebSocket, writing all data to the given output_pipe.  Exits on WebSocket
 * disconnect.
 * In "JSON_IO" mode, we open a given output_pipe and input_pipe.  These don't need to be pipes, but the
 * design intends for this to be the case since we will always open-read/write-close for each message:
 * -open input pipe, read all bytes to EOF:
 *   -if starts with "-", interpret as a command:
 *     -CLOSE - request a client-side close of the socket
 *     -WAIT - waits for the server-side close of the socket
 *     -ACK -acknowledge a previous message
 *   -else, send this as a String message
 *   -loop
 * -wait for message, open output pipe, write message, close, loop, wait for ack, exit when socket closes
 * NOTE:  In JSON_IO mode, a zero-byte message will ALWAYS be required in order to close down correctly.
 * Returns 0 on success, 1 on arg failure, 2 on WebSocket error.
 */
public class WebSocketUtility implements WebSocketListener
{
	public static final String COMMAND_CLOSE = "-CLOSE";
	public static final String COMMAND_WAIT = "-WAIT";
	public static final String COMMAND_ACK = "-ACK";

	private final File _outputPipe;
	private final WebSocketClient _client;
	private Session _connectedSession;
	private boolean _errorWasObserved;
	private final Thread _inputReaderThread;

	private boolean _isConnected;
	private boolean _awaitingAck;
	private boolean _isShuttingDown;


	public WebSocketUtility(File outputPipe, File inputPipe)
	{
		_outputPipe = outputPipe;
		_client = new WebSocketClient();
		
		// We will create the input reader but won't START it until the connection is established.  This allows us to
		// make internal decisions around whether or not we are planning to process input, later on.
		if (null != inputPipe)
		{
			_inputReaderThread = MiscHelpers.createThread(() -> {
				boolean shouldContinue = true;
				while (shouldContinue)
				{
					try
					{
						byte[] data = Files.readAllBytes(inputPipe.toPath());
						String stringValue = new String(data);
						if (stringValue.startsWith("-"))
						{
							// Command.
							if (stringValue.equals(COMMAND_CLOSE))
							{
								_connectedSession.close();
								shouldContinue = false;
							}
							else if (stringValue.equals(COMMAND_WAIT))
							{
								shouldContinue = false;
							}
							else if (stringValue.equals(COMMAND_ACK))
							{
								synchronized(WebSocketUtility.this)
								{
									// We should already awaiting the ack.
									Assert.assertTrue(_awaitingAck);
									_awaitingAck = false;
									WebSocketUtility.this.notifyAll();
								}
							}
							else
							{
								throw Assert.unreachable();
							}
						}
						else
						{
							// Raw JSON value.
							_connectedSession.getRemote().sendString(stringValue);
						}
					}
					catch (IOException e)
					{
						// If something went wrong, we will also just exit.
						e.printStackTrace();
						shouldContinue = false;
					}
					
				}
				// We dropped out of the loop so we will set the flag that we are shutting down.
				synchronized(WebSocketUtility.this)
				{
					_isShuttingDown = true;
					WebSocketUtility.this.notifyAll();
				}
			}, "WebSocketUtility Input Reader");
		}
		else
		{
			_inputReaderThread = null;
		}
		_isConnected = false;
		_awaitingAck = false;
		_isShuttingDown = false;
	}

	public void connect(String uri, String xsrf, String protocol) throws Exception
	{
		_client.start();
		ClientUpgradeRequest req = new ClientUpgradeRequest();
		req.setCookies(Collections.singletonList(new HttpCookie("XSRF", xsrf)));
		req.setSubProtocols(protocol);
		_client.connect(this, new URI(uri), req);
		_isConnected = true;
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason)
	{
		WebSocketListener.super.onWebSocketClose(statusCode, reason);
		synchronized (this)
		{
			_isConnected = false;
			this.notifyAll();
		}
	}

	@Override
	public void onWebSocketConnect(Session session)
	{
		WebSocketListener.super.onWebSocketConnect(session);
		session.setIdleTimeout(Duration.ofDays(1));
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
			synchronized (this)
			{
				while (!_isShuttingDown && _awaitingAck)
				{
					try
					{
						this.wait();
					}
					catch (InterruptedException e)
					{
						// Doesn't use interruption.
						throw Assert.unexpected(e);
					}
				}
				if (!_isShuttingDown)
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
				// If we have an input reader, we will wait for ack.
				// Reasoning:
				// With FIFOs (which is what we normally use here), attempting open(write) while an external process
				// has the open(read) still open, can allow multiple writers to be observed by the same reader, but we
				// want to force each reader and writer to see precisely one partner performing the other operation.
				// The ack forces the caller to explicitly lock-step with us so this doesn't happen.
				_awaitingAck = (null != _inputReaderThread);
			}
		}
	}

	public boolean waitForClose() throws Exception
	{
		synchronized (this)
		{
			while (_isConnected)
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
		while (!_isShuttingDown && (null == _connectedSession))
		{
			this.wait();
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
			File outputPipe = null;
			File inputPipe = null;
			if (("SEND".equals(mode)) && (4 == args.length))
			{
				sendMode = true;
			}
			else if (("DRAIN".equals(mode)) && (5 == args.length))
			{
				outputPipe = new File(args[4]);
				Assert.assertTrue(outputPipe.exists());
			}
			else if (("JSON_IO".equals(mode)) && (6 == args.length))
			{
				outputPipe = new File(args[4]);
				Assert.assertTrue(outputPipe.exists());
				inputPipe = new File(args[5]);
				Assert.assertTrue(inputPipe.exists());
			}
			else
			{
				_usageExit();
			}
			
			WebSocketUtility utility = new WebSocketUtility(outputPipe, inputPipe);
			utility.connect(uri, xsrf, protocol);
			utility.waitForConnection();
			
			// If we have an output pipe, we want to just open and close it so that scripts wanting to use this
			// interactively will know we have connected (since they may otherwise be racy).
			if (null != outputPipe)
			{
				try
				{
					Files.write(outputPipe.toPath(), new byte[0], StandardOpenOption.APPEND);
				}
				catch (IOException e)
				{
					throw Assert.unexpected(e);
				}
			}
			
			// We can now run the actual operation.
			if (sendMode)
			{
				utility.sendAllBinary(System.in);
			}
			else if (null != inputPipe)
			{
				// We can now start the input processor.
				utility._inputReaderThread.start();
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
