package com.jeffdisher.cacophony.interactive;

import java.time.Duration;
import java.util.Map;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
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
public class WS_UserEntries implements IWebSocketFactory
{
	// We will start by just sending them the last 10 entries.
	private static final int START_ENTRY_LIMIT = 10;

	private final String _xsrf;
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _entryConnector;
	
	public WS_UserEntries(String xsrf, Map<IpfsKey, HandoffConnector<IpfsFile, Void>> entryConnector)
	{
		_xsrf = xsrf;
		_entryConnector = entryConnector;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		IpfsKey key = IpfsKey.fromPublicKey(variables[0]);
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
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				_userConnector = _entryConnector.get(_key);
				if (null != _userConnector)
				{
					_endPoint = session.getRemote();
					// Note that this call to registerListener will likely involves calls back into us, relying on the _endPoint.
					_userConnector.registerListener(this, START_ENTRY_LIMIT);
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
