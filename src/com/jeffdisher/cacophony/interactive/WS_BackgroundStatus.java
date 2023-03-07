package com.jeffdisher.cacophony.interactive;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.Json;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Just listens to updates to background operations.
 * WARNING:  This assumes that there is only a single client at a time, such that creating a new one will effectively
 * invalidate a previous one and closing any of them will invalidate them all.  This is something we should eventually
 * fix but it doesn't really matter to our use-case so it will be deferred in case we make other changes.
 */
public class WS_BackgroundStatus implements IWebSocketFactory
{
	public final static String COMMAND_STOP = "COMMAND_STOP";
	public final static String COMMAND_REPUBLISH = "COMMAND_REPUBLISH";

	private final IEnvironment _environment;
	private final String _xsrf;
	private final HandoffConnector<Integer, String> _statusHandoff;
	private final CountDownLatch _stopLatch;
	private final BackgroundOperations _background;
	
	public WS_BackgroundStatus(IEnvironment environment, String xsrf, HandoffConnector<Integer, String> statusHandoff, CountDownLatch stopLatch, BackgroundOperations background)
	{
		_environment = environment;
		_xsrf = xsrf;
		_statusHandoff = statusHandoff;
		_stopLatch = stopLatch;
		_background = background;
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
			else if (COMMAND_REPUBLISH.equals(message))
			{
				// Look up the last published root and request that background operations republish it.
				IpfsFile root = null;
				try (IReadingAccess access = StandardAccess.readAccess(_environment))
				{
					root = access.getLastRootElement();
				}
				catch (UsageException | VersionException e)
				{
					// Not expected after startup.
					throw Assert.unexpected(e);
				}
				catch (IpfsConnectionException e)
				{
					// This error probably means serious issues so close the socket.
					_session.close(WebSocketCodes.IPFS_CONNECTION_ERROR, e.getLocalizedMessage());
				}
				if (null != root)
				{
					_background.requestPublish(root);
				}
			}
			else
			{
				// This is unknown, so close the socket with an error.
				_session.close(WebSocketCodes.INVALID_COMMAND, "Invalid command");
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
