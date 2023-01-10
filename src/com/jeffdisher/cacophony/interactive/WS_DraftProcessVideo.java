package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

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
		return new ProcessVideoWebSocketListener(draftId, processCommand);
	}


	private class ProcessVideoWebSocketListener implements WebSocketListener
	{
		private final int _draftId;
		private final String _processCommand;
		private final VideoProcessContainer _videoProcessContainer;
		private VideoProcessorCallbackHandler _handler;
		
		public ProcessVideoWebSocketListener(int draftId, String processCommand)
		{
			_draftId = draftId;
			_processCommand = processCommand;
			_videoProcessContainer = new VideoProcessContainer(_draftManager);
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			if (null != _handler)
			{
				_videoProcessContainer.cancelProcess();
				_videoProcessContainer.detachListener(_handler);
				_handler = null;
			}
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				Assert.assertTrue(null == _handler);
				try
				{
					boolean didStart = _videoProcessContainer.startProcess(_draftId, _processCommand);
					if (didStart)
					{
						_handler = new VideoProcessorCallbackHandler(session);
						boolean didAttach = _videoProcessContainer.attachListener(_handler, _draftId);
						// This can only fail in the case where the command immediately failed before we ran this next
						// line - we don't expect to see this and would like to study it if it happens.
						Assert.assertTrue(didAttach);
					}
					else
					{
						session.close(CloseStatus.SERVER_ERROR, "Already running");
					}
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
}
