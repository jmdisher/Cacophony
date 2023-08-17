package com.jeffdisher.cacophony.interactive;

import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.cacophony.caches.ReplyForest;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Registers to listen to the tree of replies rooted at the CID given in the initial path.
 * This uses the "event_api" protocol but always sends all replies, immediately, and does accept the scroll back
 * message.  Details of messages:
 * -key is the message CID being added/removed
 * -value is the CID of the parent of the added message
 * Note that there are no updates or special strings.
 */
public class WS_ReplyTree implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final ConnectorDispatcher _dispatcher;
	private final ReplyForest _replyForest;

	public WS_ReplyTree(ConnectorDispatcher dispatcher, ReplyForest replyForest)
	{
		_dispatcher = dispatcher;
		_replyForest = replyForest;
	}

	@Override
	public WebSocketListener build(Object[] path)
	{
		HandoffConnector<IpfsFile, IpfsFile> handoffConnector = new HandoffConnector<IpfsFile, IpfsFile>(_dispatcher);
		IpfsFile root = (IpfsFile)path[3];
		return new Listener(handoffConnector, _replyForest, root);
	}


	private class Listener implements WebSocketListener, HandoffConnector.IHandoffListener<IpfsFile, IpfsFile>
	{
		private final HandoffConnector<IpfsFile, IpfsFile> _handoffConnector;
		private final ReplyForest _replyForest;
		private final IpfsFile _root;
		private RemoteEndpoint _endPoint;
		private ReplyForest.IAdapterToken _adapter;
		
		public Listener(HandoffConnector<IpfsFile, IpfsFile> handoffConnector, ReplyForest replyForest, IpfsFile root)
		{
			_handoffConnector = handoffConnector;
			_replyForest = replyForest;
			_root = root;
		}
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_replyForest.removeListener(_adapter);
			_adapter = null;
			_handoffConnector.unregisterListener(this);
		}
		@Override
		public void onWebSocketConnect(Session session)
		{
			// We capture the endpoint, first, since registering the connector will immediately result in calls back
			// into this object to send messages over the endpoint.
			_endPoint = session.getRemote();
			
			// WARNING:  We register this listener without a message limit since we want to build the entire tree, in-order.
			// We also register the listener BEFORE we give the connector to the forest since we want it to immediately
			// pass on all the data it receives to the client.  This also means that we do NOT handle the scroll back command.
			_handoffConnector.registerListener(this, 0);
			_adapter = _replyForest.addListener(_handoffConnector, _root);
			
			// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
			session.setIdleTimeout(Duration.ofDays(1));
		}
		@Override
		public void onWebSocketText(String message)
		{
			// This endpoint should never receive the scroll back command since it has no meaning in this case.
			throw Assert.unreachable();
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
