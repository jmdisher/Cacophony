package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to background operations.
 * Receives messages for commands to run:
 * -COMMAND_STOP - tells the server to shut down
 * -COMMAND_REPUBLISH - tells the server to republish this user's root element
 * Messages:
 * -create key(action_id) -> value(action_description)
 * -NO update
 * -delete key(action_id)
 * -NO special
 */
public class WS_BackgroundStatus implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final HandoffConnector<Integer, String> _statusHandoff;
	
	public WS_BackgroundStatus(HandoffConnector<Integer, String> statusHandoff
	)
	{
		_statusHandoff = statusHandoff;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		return new StatusListener();
	}


	private class StatusListener implements WebSocketListener, HandoffConnector.IHandoffListener<Integer, String>
	{
		private RemoteEndpoint _endPoint;
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_statusHandoff.unregisterListener(this);
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			_endPoint = session.getRemote();
			// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
			_statusHandoff.registerListener(this, 0);
			// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
			session.setIdleTimeout(Duration.ofDays(1));
		}
		
		@Override
		public boolean create(Integer key, String value, boolean isNewest)
		{
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key), Json.value(value), isNewest);
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
		
		@Override
		public boolean specialChanged(String special)
		{
			// This case doesn't use meta-data.
			throw Assert.unreachable();
		}
	}
}
