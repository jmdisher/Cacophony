package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to the list of cached entries for a given user, reporting the added/removed record CIDs.
 * The user is identified by public key and can be either a followee or the home user.
 * Messages:
 * -create key(record_cid) -> value(null)
 * -NO update
 * -delete key(record_cid)
 * -special is set when the refresh for this user is active, set null when complete
 */
public class WS_UserEntries implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	// We will start by just sending them the last 10 entries.
	private static final int START_ENTRY_LIMIT = 10;
	// We will use only 5 entries for unknown user entries.
	private static final int UNKNOWN_ENTRY_LIMIT = 5;
	// We use this command to request we scroll back further.
	private static final String COMMAND_SCROLL_BACK = "COMMAND_SCROLL_BACK";

	private final Context _context;
	private final ConnectorDispatcher _dispatcher;
	
	public WS_UserEntries(Context context
			, ConnectorDispatcher dispatcher
	)
	{
		_context = context;
		_dispatcher = dispatcher;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		IpfsKey key = (IpfsKey) path[3];
		return new Listener(key);
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsFile, Void>
	{
		private final IpfsKey _key;
		private HandoffConnector<IpfsFile, Void> _userConnector;
		private RemoteEndpoint _endPoint;
		
		public Listener(IpfsKey key)
		{
			_key = key;
		}
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			if (null != _userConnector)
			{
				_userConnector.unregisterListener(this);
			}
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			// See if we can get a normal read-only connector for this.
			_userConnector = _context.entryRegistry.getReadOnlyConnector(_key);
			if (null != _userConnector)
			{
				// The connector is available so this is either a home user or a followee.
				_endPoint = session.getRemote();
				// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
				_userConnector.registerListener(this, START_ENTRY_LIMIT);
				// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
				session.setIdleTimeout(Duration.ofDays(1));
			}
			else
			{
				// This must not be a home user or a followee so see if we can extract any explicit cache information about them.
				try
				{
					ExplicitCacheData.UserInfo info = _context.explicitCacheManager.loadUserInfo(_key).get();
					// This would throw, but not return null.
					Assert.assertTrue(null != info);
					
					// Create the connector, ourselves.
					_userConnector = new HandoffConnector<IpfsFile, Void>(_dispatcher);
					// Read the post list and feed them into the connector (which will do the rest of this work for us).
					try (IReadingAccess access = StandardAccess.readAccess(_context))
					{
						AbstractRecords records = access.loadCached(info.recordsCid(), AbstractRecords.DESERIALIZER).get();
						// This list goes from oldest to newest so just walk in-order.
						for (IpfsFile cid : records.getRecordList())
						{
							_userConnector.create(cid, null);
						}
					}
					catch (FailedDeserializationException | IpfsConnectionException e)
					{
						// We already pinned this so this can't fail unless the local node is killed.
						throw Assert.unexpected(e);
					}
					
					// Do normal setup, but we use a smaller list of entries, since these aren't locally known and each hit will go to the explicit cache..
					_endPoint = session.getRemote();
					_userConnector.registerListener(this, UNKNOWN_ENTRY_LIMIT);
					session.setIdleTimeout(Duration.ofDays(1));
				}
				catch (KeyException | ProtocolDataException | IpfsConnectionException e1)
				{
					// We don't know anything about this user.
					session.close(WebSocketCodes.NOT_FOUND, "User not known");
				}
			}
		}
		
		@Override
		public void onWebSocketText(String message)
		{
			// We only have a single command so anything else would be a static feature mismatch.
			Assert.assertTrue(COMMAND_SCROLL_BACK.equals(message));
			_userConnector.requestOlderElements(this, START_ENTRY_LIMIT);
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
			// This is used when describing that the refresh is running.
			return SocketEventHelpers.sendSpecial(_endPoint, Json.value(special));
		}
	}
}
