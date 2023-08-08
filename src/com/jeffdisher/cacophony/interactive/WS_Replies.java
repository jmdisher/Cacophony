package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Sends updates the client related to reply relationships.
 * Specifically, any replies from followees to any post created by a home user as communicated as a create:
 * -key is the CID of the followee reply
 * -value is the CID of the home user post
 * Destroy just sends the CID of the followee reply.
 */
public class WS_Replies implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	// We will start by just sending them the last 10 entries.
	private static final int START_ENTRY_LIMIT = 10;
	// We use this command to request we scroll back further.
	private static final String COMMAND_SCROLL_BACK = "COMMAND_SCROLL_BACK";

	private final HandoffConnector<IpfsFile, IpfsFile> _replyCacheConnector;
	
	public WS_Replies(HandoffConnector<IpfsFile, IpfsFile> replyCacheConnector)
	{
		_replyCacheConnector = replyCacheConnector;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		return new Listener();
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsFile, IpfsFile>
	{
		private RemoteEndpoint _endPoint;
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_replyCacheConnector.unregisterListener(this);
		}
		@Override
		public void onWebSocketConnect(Session session)
		{
			_endPoint = session.getRemote();
			// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
			_replyCacheConnector.registerListener(this, START_ENTRY_LIMIT);
			// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
			session.setIdleTimeout(Duration.ofDays(1));
		}
		@Override
		public void onWebSocketText(String message)
		{
			// We only have a single command so anything else would be a static feature mismatch.
			Assert.assertTrue(COMMAND_SCROLL_BACK.equals(message));
			_replyCacheConnector.requestOlderElements(this, START_ENTRY_LIMIT);
		}
		@Override
		public boolean create(IpfsFile key, IpfsFile value, boolean isNewest)
		{
			// Added.
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key.toSafeString()), Json.value(value.toSafeString()), isNewest);
		}
		@Override
		public boolean update(IpfsFile key, IpfsFile value)
		{
			// Updates don't happen in this case.
			throw Assert.unreachable();
		}
		@Override
		public boolean destroy(IpfsFile key)
		{
			// Removed.
			return SocketEventHelpers.sendDelete(_endPoint, Json.value(key.toSafeString()));
		}
		@Override
		public boolean specialChanged(String special)
		{
			// Special isn't used in this case.
			throw Assert.unreachable();
		}
	}
}
