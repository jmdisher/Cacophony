package com.jeffdisher.cacophony.interactive;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to background operations.
 * WARNING:  This assumes that there is only a single client at a time, such that creating a new one will effectively
 * invalidate a previous one and closing any of them will invalidate them all.  This is something we should eventually
 * fix but it doesn't really matter to our use-case so it will be deferred in case we make other changes.
 */
public class WS_BackgroundStatus implements IWebSocketFactory
{
	// WebSocket close codes in the range 4000-4999 are "private" and are for use in non-registered applications so we just use 4000 as our "invalid command" opcode.
	public final static int WS_CLOSE_INVALID_COMMAND = 4000;
	public final static String COMMAND_STOP = "COMMAND_STOP";

	private final String _xsrf;
	private final HandoffConnector<Integer, String> _statusHandoff;
	private final CountDownLatch _stopLatch;
	
	public WS_BackgroundStatus(String xsrf, HandoffConnector<Integer, String> statusHandoff, CountDownLatch stopLatch)
	{
		_xsrf = xsrf;
		_statusHandoff = statusHandoff;
		_stopLatch = stopLatch;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		return new StatusListener();
	}


	private class StatusListener implements WebSocketListener, HandoffConnector.IHandoffListener<Integer, String>
	{
		private Session _session;
		private RemoteEndpoint _endPoint;
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_statusHandoff.unregisterListener(this);
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				_session = session;
				_endPoint = session.getRemote();
				// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
				_statusHandoff.registerListener(this);
				// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
				session.setIdleTimeout(Duration.ofDays(1));
			}
		}
		
		@Override
		public void onWebSocketText(String message)
		{
			// We currently only check for a bunch of keywords here, not structured data.
			if (COMMAND_STOP.equals(message))
			{
				// Stop the server.
				_stopLatch.countDown();
			}
			else
			{
				// This is unknown, so close the socket with an error.
				_session.close(WS_CLOSE_INVALID_COMMAND, "Invalid command");
			}
		}
		
		@Override
		public boolean create(Integer key, String value)
		{
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key), Json.value(value));
		}
		
		@Override
		public boolean update(Integer key, String value)
		{
			// We don't have updates for this event.
			throw Assert.unreachable();
		}
		
		@Override
		public boolean destroy(Integer key)
		{
			return SocketEventHelpers.sendDelete(_endPoint, Json.value(key));
		}
	}
}
