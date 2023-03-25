package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to the list of combined "recent activity" across all followed users and this local user,
 * reporting the added/removed record CIDs.
 * Messages:
 * -create key(record_cid) -> value(null)
 * -NO update
 * -delete key(record_cid)
 * -special is not used
 */
public class WS_CombinedEntries implements IWebSocketFactory
{
	// We will start by just sending them the last 10 entries.
	private static final int START_ENTRY_LIMIT = 10;
	// We use this command to request we scroll back further.
	private static final String COMMAND_SCROLL_BACK = "COMMAND_SCROLL_BACK";

	private final String _xsrf;
	private final EntryCacheRegistry _entryRegistry;
	
	public WS_CombinedEntries(String xsrf, EntryCacheRegistry entryRegistry)
	{
		_xsrf = xsrf;
		_entryRegistry = entryRegistry;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		return new Listener();
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsFile, Void>
	{
		private HandoffConnector<IpfsFile, Void> _connector;
		private RemoteEndpoint _endPoint;
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			if (null != _connector)
			{
				_connector.unregisterListener(this);
			}
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				_connector = _entryRegistry.getCombinedConnector();
				if (null != _connector)
				{
					_endPoint = session.getRemote();
					// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
					_connector.registerListener(this, START_ENTRY_LIMIT);
					// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
					session.setIdleTimeout(Duration.ofDays(1));
				}
				else
				{
					session.close(WebSocketCodes.NOT_FOUND, "User not known");
				}
			}
		}
		
		@Override
		public void onWebSocketText(String message)
		{
			// We only have a single command so anything else would be a static feature mismatch.
			Assert.assertTrue(COMMAND_SCROLL_BACK.equals(message));
			_connector.requestOlderElements(this, START_ENTRY_LIMIT);
		}
		
		@Override
		public boolean create(IpfsFile key, Void value, boolean isNewest)
		{
			// Added.
			return SocketEventHelpers.sendCreate(_endPoint, Json.value(key.toSafeString()), Json.NULL, isNewest);
		}
		
		@Override
		public boolean update(IpfsFile key, Void value)
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
			// There is no combined special.
			throw Assert.unreachable();
		}
	}
}
