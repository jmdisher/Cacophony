package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to followee refresh events to report the refresh time.
 * Messages:
 * -create key(public_key) -> value({poll_millis,success_millis})
 * -update key(public_key) -> value({poll_millis,success_millis})
 * -delete key(public_key)
 * -NO special
 */
public class WS_FolloweeRefreshTimes implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final HandoffConnector<IpfsKey, FolloweeData.TimePair> _followeeRefreshConnector;
	
	public WS_FolloweeRefreshTimes(HandoffConnector<IpfsKey, FolloweeData.TimePair> followeeRefreshConnector)
	{
		_followeeRefreshConnector = followeeRefreshConnector;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		return new Listener();
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsKey, FolloweeData.TimePair>
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
			_endPoint = session.getRemote();
			// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
			_followeeRefreshConnector.registerListener(this, 0);
			// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
			session.setIdleTimeout(Duration.ofDays(1));
		}
		
		@Override
		public boolean create(IpfsKey key, FolloweeData.TimePair value, boolean isNewest)
		{
			// Initial value.
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key.toPublicKey()), _timeJson(value), isNewest);
		}
		
		@Override
		public boolean update(IpfsKey key, FolloweeData.TimePair value)
		{
			// When refresh operations complete.
			return SocketEventHelpers.sendUpdate(_endPoint, Json.value(key.toPublicKey()), _timeJson(value));
		}
		
		@Override
		public boolean destroy(IpfsKey key)
		{
			// Happens when the followee is deleted from the FolloweeData.
			return SocketEventHelpers.sendDelete(_endPoint, Json.value(key.toPublicKey()));
		}
		
		@Override
		public boolean specialChanged(String special)
		{
			// This case doesn't use meta-data.
			throw Assert.unreachable();
		}
		
		private JsonObject _timeJson(FolloweeData.TimePair value)
		{
			JsonObject obj = new JsonObject();
			obj.add("poll_millis", value.pollMillis());
			obj.add("success_millis", value.successMillis());
			return obj;
		}
	}
}
