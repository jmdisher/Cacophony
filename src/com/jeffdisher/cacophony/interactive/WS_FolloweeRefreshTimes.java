package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to followee refresh events to report the refresh time.
 */
public class WS_FolloweeRefreshTimes implements IWebSocketFactory
{
	private final String _xsrf;
	private final HandoffConnector<IpfsKey, Long> _followeeRefreshConnector;
	
	public WS_FolloweeRefreshTimes(String xsrf, HandoffConnector<IpfsKey, Long> followeeRefreshConnector)
	{
		_xsrf = xsrf;
		_followeeRefreshConnector = followeeRefreshConnector;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		return new Listener();
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsKey, Long>
	{
		private RemoteEndpoint _endPoint;
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_followeeRefreshConnector.unregisterListener(this);
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				_endPoint = session.getRemote();
				// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
				_followeeRefreshConnector.registerListener(this);
				// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
				session.setIdleTimeout(Duration.ofDays(1));
			}
		}
		
		@Override
		public boolean create(IpfsKey key, Long value)
		{
			// Initial value.
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key.toPublicKey()), Json.value(value));
		}
		
		@Override
		public boolean update(IpfsKey key, Long value)
		{
			// When refresh operations complete.
			return SocketEventHelpers.sendUpdate(_endPoint, Json.value(key.toPublicKey()), Json.value(value));
		}
		
		@Override
		public boolean destroy(IpfsKey key)
		{
			// There shouldn't be any cases where this happens (yet).
			throw Assert.unreachable();
		}
	}
}