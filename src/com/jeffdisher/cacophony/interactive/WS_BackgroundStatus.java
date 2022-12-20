package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IWebSocketFactory;


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
	private final BackgroundOperations _background;
	
	public WS_BackgroundStatus(String xsrf, BackgroundOperations background)
	{
		_xsrf = xsrf;
		_background = background;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		return new StatusListener(_xsrf, _background);
	}


	private static class StatusListener implements WebSocketListener, BackgroundOperations.IOperationListener
	{
		private final String _xsrf;
		private final BackgroundOperations _background;
		private RemoteEndpoint _endPoint;
		
		public StatusListener(String xsrf, BackgroundOperations background)
		{
			_xsrf = xsrf;
			_background = background;
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			_background.setListener(null);
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				_endPoint = session.getRemote();
				_background.setListener(this);
				// Set a 1-day idle timeout, just to avoid this constantly dropping when looking at it.
				session.setIdleTimeout(Duration.ofDays(1));
			}
		}
		
		@Override
		public void operationEnqueued(int number, String description)
		{
			_writeEvent(number, Event.ENQUEUE, description);
		}
		
		@Override
		public void operationStart(int number)
		{
			_writeEvent(number, Event.START, null);
		}
		
		@Override
		public void operationEnd(int number)
		{
			_writeEvent(number, Event.END, null);
		}
		
		private void _writeEvent(int number, Event event, String description)
		{
			JsonObject root = new JsonObject();
			root.add("number", number);
			root.add("event", event.name());
			root.add("description", description);
			try
			{
				_endPoint.sendString(root.toString());
			}
			catch (IOException e)
			{
				// If something went wrong, unset the listener so we can stop sending data.
				_background.setListener(null);
			}
		}
	}


	private static enum Event
	{
		ENQUEUE,
		START,
		END,
	}
}
