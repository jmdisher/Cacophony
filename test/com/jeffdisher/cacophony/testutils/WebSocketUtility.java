package com.jeffdisher.cacophony.testutils;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;


/**
 * This is meant to be built as a stand-alone utility for use in integration tests.  It is a utility for various
 * interactions with WebSockets.
 * Args:
 * [0] - XSRF token
 * [1] - SEND/DRAIN
 * [2] - URI
 * [3] - protocol
 * 
 * In "SEND" mode, will read stdin, sending this data to the other side as binary.  Closes the socket on EOF.
 * In "DRAIN" mode, will connect and read the socket until it closes.
 * Returns 0 on success, 1 on arg failure, 2 on WebSocket error.
 */
public class WebSocketUtility implements WebSocketListener
{
	private final PrintStream _textOutput;
	private final WebSocketClient _client;
	private boolean _isRunning;
	private Session _connectedSession;
	private boolean _errorWasObserved;


	public WebSocketUtility(PrintStream textOutput)
	{
		_textOutput = textOutput;
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
		if (null != _textOutput)
		{
			_textOutput.println(message);
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
		return _errorWasObserved;
	}

	public void sendAllBinary(InputStream in) throws Exception
	{
		synchronized (this)
		{
			while (_isRunning && (null == _connectedSession))
			{
				this.wait();
			}
		}
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


	public static void main(String[] args) throws Exception
	{
		if (4 != args.length)
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
			PrintStream textOutput = null;
			if ("SEND".equals(mode))
			{
				sendMode = true;
			}
			else if ("DRAIN".equals(mode))
			{
				sendMode = false;
			}
			else if ("OUTPUT_TEXT".equals(mode))
			{
				sendMode = false;
				textOutput = System.out;
			}
			else
			{
				_usageExit();
			}
			
			WebSocketUtility utility = new WebSocketUtility(textOutput);
			utility.connect(uri, xsrf, protocol);
			if (sendMode)
			{
				utility.sendAllBinary(System.in);
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
		System.err.println("Usage: XSRF_TOKEN SEND/DRAIN/OUTPUT_TEXT URI protocol");
		System.exit(1);
	}
}
