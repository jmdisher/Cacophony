package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

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
 * TODO:  Fix this multi-client case.
 */
public class WS_BackgroundStatus implements IWebSocketFactory
{
	private final String _xsrf;
	private final HandoffConnector<Integer, String> _statusHandoff;
	
	public WS_BackgroundStatus(String xsrf, HandoffConnector<Integer, String> statusHandoff)
	{
		_xsrf = xsrf;
		_statusHandoff = statusHandoff;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		return new StatusListener(_xsrf, _statusHandoff);
	}


	private static class StatusListener implements WebSocketListener, HandoffConnector.IHandoffListener<Integer, String>
	{
		private final String _xsrf;
		private final HandoffConnector<Integer, String> _statusHandoff;
		private RemoteEndpoint _endPoint;
		
		public StatusListener(String xsrf, HandoffConnector<Integer, String> statusHandoff)
		{
			_xsrf = xsrf;
			_statusHandoff = statusHandoff;
		}
		
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
				_endPoint = session.getRemote();
				// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
				_statusHandoff.registerListener(this);
				// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
				session.setIdleTimeout(Duration.ofDays(1));
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
