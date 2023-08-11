package com.jeffdisher.cacophony.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * This is a utility for use in tests.  It runs a WebSocket client which just captures all text-based WebSocket messages
 * in an array and exposes a REST server to allow fetching of the elements in that array.
 * This is largely meant to replace the old WebSocketUtility as the pipe interactions were long-winded and complicated.
 * Args:
 * [0] - XSRF token
 * [1] - URI of the WebSocket
 * [2] - protocol
 * [3] - PORT to listen on for REST
 * 
 * End-points it exposes:
 * -GET "/waitAndGet/<index>" - returns the text of message at the given index, blocking if it hasn't yet arrived.
 * -GET "/count" - returns the number of messages received, so far.
 * -POST "/send" - sends the given raw data as a text message.
 * -POST "/close" - closes the WebSocket connection.
 * 
 * The program will shut down the server and exit with (0) when the socket closes or exit with (2) if there is an error.
 */
public class WebSocketToRestUtility implements WebSocketListener
{
	private final WebSocketClient _client;
	private Session _connectedSession;
	private boolean _errorWasObserved;
	private final List<String> _messages;

	public WebSocketToRestUtility()
	{
		_client = new WebSocketClient();
		_messages = new ArrayList<>();
	}

	public void connect(String uri, String xsrf, String protocol) throws Exception
	{
		_client.start();
		ClientUpgradeRequest req = new ClientUpgradeRequest();
		req.setCookies(Collections.singletonList(new HttpCookie("XSRF", xsrf)));
		req.setSubProtocols(protocol);
		_client.connect(this, new URI(uri), req);
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason)
	{
		WebSocketListener.super.onWebSocketClose(statusCode, reason);
		synchronized (this)
		{
			_connectedSession = null;
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
	public synchronized void onWebSocketText(String message)
	{
		// Just add this to the message list.
		_messages.add(message);
		// Notify anyone waiting.
		this.notifyAll();
	}

	public boolean waitForClose() throws Exception
	{
		boolean wasNormalClose = false;
		synchronized (this)
		{
			while ((null != _connectedSession) && !_errorWasObserved)
			{
				this.wait();
			}
			wasNormalClose = !_errorWasObserved;
		}
		_client.stop();
		return wasNormalClose;
	}

	public synchronized boolean waitForConnection() throws InterruptedException
	{
		while ((null == _connectedSession) && !_errorWasObserved)
		{
			this.wait();
		}
		return !_errorWasObserved;
	}

	public synchronized String getMessage(int index)
	{
		// Wait until this message arrives.
		while ((_messages.size() <= index) && (null != _connectedSession))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				throw Assert.unexpected(e);
			}
		}
		return (null != _connectedSession)
				? _messages.get(index)
				: null
		;
	}

	public synchronized int getMessageCount()
	{
		return _messages.size();
	}

	public void sendMessage(String message)
	{
		try
		{
			_connectedSession.getRemote().sendString(message);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}

	public void closeSocket()
	{
		_connectedSession.close();
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
			String uri = args[1];
			String protocol = args[2];
			int port = Integer.parseInt(args[3]);
			
			// Open the WebSocket.
			WebSocketToRestUtility utility = new WebSocketToRestUtility();
			utility.connect(uri, xsrf, protocol);
			utility.waitForConnection();
			
			// Now, start the REST server.
			InetSocketAddress interfaceToBind = new InetSocketAddress("127.0.0.1", port);
			RestServer server = new RestServer(interfaceToBind, null, null);
			server.installPathParser("int", (String raw) -> Integer.parseInt(raw));
			
			server.addGetHandler("/waitAndGet/{int}", new IGetHandler() {
				@Override
				public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
				{
					String message = utility.getMessage((Integer)path[1]);
					response.getWriter().write(message);
				}
			});
			server.addGetHandler("/count", new IGetHandler() {
				@Override
				public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
				{
					int size = utility.getMessageCount();
					response.getWriter().write(size);
				}
			});
			server.addPostRawHandler("/send", new IPostRawHandler() {
				@Override
				public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
				{
					InputStream input = request.getInputStream();
					// We only handle text-based messages.
					String message = new String(input.readAllBytes());
					utility.sendMessage(message);
				}
			});
			server.addPostRawHandler("/close", new IPostRawHandler() {
				@Override
				public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
				{
					utility.closeSocket();
				}
			});
			server.start();
			
			// Wait for close.
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
		System.err.println("Usage: XSRF_TOKEN URI PROTOCOL PORT");
		System.exit(1);
	}
}
