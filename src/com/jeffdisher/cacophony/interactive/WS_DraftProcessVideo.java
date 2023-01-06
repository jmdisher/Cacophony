package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Opens a video processing web socket for the given draft ID and command.
 */
public class WS_DraftProcessVideo implements IWebSocketFactory
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	private final String _forcedCommand;
	
	public WS_DraftProcessVideo(String xsrf, DraftManager draftManager, String forcedCommand)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
		_forcedCommand = forcedCommand;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		int draftId = Integer.parseInt(variables[0]);
		String processCommand = variables[1];
		// See if we are supposed to override this connection.
		if (null != _forcedCommand)
		{
			processCommand = _forcedCommand;
		}
		System.out.println("Opening processing socket with local command: \"" + processCommand + "\"");
		return new ProcessVideoWebSocketListener(_xsrf, _draftManager, draftId, processCommand);
	}


	private static class ProcessVideoWebSocketListener implements WebSocketListener
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final int _draftId;
		private final String _processCommand;
		private VideoProcessor _processor;
		
		public ProcessVideoWebSocketListener(String xsrf, DraftManager draftManager, int draftId, String processCommand)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_draftId = draftId;
			_processCommand = processCommand;
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			InteractiveHelpers.closeVideoProcessor(_processor);
			_processor = null;
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				Assert.assertTrue(null == _processor);
				try
				{
					_processor = InteractiveHelpers.openVideoProcessor(new ProcessorCallbackHandler(session), _draftManager, _draftId, _processCommand);
				}
				catch (FileNotFoundException e)
				{
					// This happens in the case where the draft doesn't exist.
					session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
				}
				catch (IOException e)
				{
					// This happened if we failed to run the processor.
					session.close(CloseStatus.SERVER_ERROR, "Failed to run processing program: \"" + _processCommand + "\"");
				}
				// Set a 1-day idle timeout, just to avoid this dropping on slower systems while waiting for the
				// processing to finish the last bit of input (since we don't see progress in those last few seconds).
				session.setIdleTimeout(Duration.ofDays(1));
			}
		}
	}


	private static class ProcessorCallbackHandler implements VideoProcessor.ProcessWriter
	{
		private final Session _session;
		
		public ProcessorCallbackHandler(Session session)
		{
			_session = session;
		}
		
		@Override
		public void totalBytesProcessed(long bytesProcessed)
		{
			JsonObject object = new JsonObject();
			object.add("type", "progress");
			object.add("bytes", bytesProcessed);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// This should be able to happen, and just means the connection has dropped.
				// We haven't observed this case in testing but we know that it is the cause of the exception in the
				// other cases.
			}
		}
		
		@Override
		public void processingError(String error)
		{
			JsonObject object = new JsonObject();
			object.add("type", "error");
			object.add("string", error);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// This should be able to happen, and just means the connection has dropped.
				// In fact, we see an error in the case when an early disconnect causes the background process to be
				// killed.  However, it seems as though the first message sent after the disconnect doesn't trigger the
				// exception so we typically don't see a failure here, but in the following "done" callback.
			}
		}
		
		@Override
		public void processingDone(long outputSizeBytes)
		{
			JsonObject object = new JsonObject();
			object.add("type", "done");
			object.add("bytes", outputSizeBytes);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// This typically happens if the processing completes after a disconnect.
				// Since we always receive this call, no matter how the process terminated, this case will always be hit
				// when the disconnect triggers termination.
			}
			_session.close();
			System.out.println("PROCESSING DONE");
		}
	}
}
