package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Opens a video processing web socket for the given draft ID and command.
 * Receives a command:
 * -COMMAND_CANCEL_PROCESSING - stops the video processing.
 * Messages:
 * -create key("inputBytes") -> value(bytes_sent_to_process)
 * -create key("outputBytes") -> value(bytes_received_from_process)
 * -update key("inputBytes") -> value(bytes_sent_to_process)
 * -delete key("inputBytes")
 * -delete key("outputBytes")
 * -NO special
 */
public class WS_DraftProcessVideo implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final Context _context;
	private final VideoProcessContainer _videoProcessContainer;
	private final String _forcedCommand;
	
	public WS_DraftProcessVideo(Context context, VideoProcessContainer videoProcessContainer, String forcedCommand)
	{
		_context = context;
		_videoProcessContainer = videoProcessContainer;
		_forcedCommand = forcedCommand;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		int draftId = (Integer)path[3];
		String processCommand = (String)path[4];
		// See if we are supposed to override this connection.
		if (null != _forcedCommand)
		{
			processCommand = _forcedCommand;
		}
		return new ProcessVideoWebSocketListener(draftId, processCommand);
	}


	private class ProcessVideoWebSocketListener implements WebSocketListener
	{
		private final int _draftId;
		private final String _processCommand;
		private final ILogger _logger;
		private VideoProcessorCallbackHandler _handler;
		
		public ProcessVideoWebSocketListener(int draftId, String processCommand)
		{
			_draftId = draftId;
			_processCommand = processCommand;
			_logger = _context.logger.logStart("Opening processing socket with local command: \"" + processCommand + "\"");
		}
		
		@Override
		public void onWebSocketText(String message)
		{
			// For now (at least), we will make the assumption that the front-end only sends us messages after it sees data.
			Assert.assertTrue(null != _handler);
			if (VideoProcessContainer.COMMAND_CANCEL_PROCESSING.equals(message))
			{
				_logger.logOperation("Cancelling video processing...");
				_videoProcessContainer.cancelProcess();
			}
			else
			{
				// Something bogus was passed in so we should look at this.
				throw Assert.unreachable();
			}
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			if (null != _handler)
			{
				_videoProcessContainer.detachListener(_handler);
				_logger.logFinish("Socket closed");
				_handler = null;
			}
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			Assert.assertTrue(null == _handler);
			try
			{
				boolean didStart = _videoProcessContainer.startProcess(_draftId, _processCommand);
				if (didStart)
				{
					_logger.logOperation("Processing start...");
					_handler = new VideoProcessorCallbackHandler(session);
					boolean didAttach = _videoProcessContainer.attachListener(_handler, _draftId);
					// This can only fail in the case where the command immediately failed before we ran this next
					// line - we don't expect to see this and would like to study it if it happens.
					Assert.assertTrue(didAttach);
					
					// Set a 1-day idle timeout, just to avoid this dropping on slower systems while waiting for the
					// processing to finish the last bit of input (since we don't see progress in those last few seconds).
					session.setIdleTimeout(Duration.ofDays(1));
				}
				else
				{
					session.close(WebSocketCodes.ALREADY_STARTED, "Already running");
				}
			}
			catch (FileNotFoundException e)
			{
				// This happens in the case where the draft doesn't exist.
				session.close(WebSocketCodes.NOT_FOUND, "Draft does not exist");
			}
			catch (IOException e)
			{
				// This happened if we failed to run the processor.
				session.close(WebSocketCodes.FAILED_TO_START, "Failed to run processing program: \"" + _processCommand + "\"");
			}
		}
	}
}
