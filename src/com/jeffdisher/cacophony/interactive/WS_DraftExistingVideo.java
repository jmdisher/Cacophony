package com.jeffdisher.cacophony.interactive;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * Tries to connect to an existing video processing command.
 * Output is identical to WS_DraftProcessVideo, once the connection is established.
 */
public class WS_DraftExistingVideo implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final VideoProcessContainer _videoProcessContainer;
	
	public WS_DraftExistingVideo(VideoProcessContainer videoProcessContainer)
	{
		_videoProcessContainer = videoProcessContainer;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		int draftId = Integer.parseInt((String)path[3]);
		return new ProcessVideoWebSocketListener(draftId);
	}


	private class ProcessVideoWebSocketListener implements WebSocketListener
	{
		private final int _draftId;
		private VideoProcessorCallbackHandler _handler;
		
		public ProcessVideoWebSocketListener(int draftId)
		{
			_draftId = draftId;
		}
		
		@Override
		public void onWebSocketText(String message)
		{
			// For now (at least), we will make the assumption that the front-end only sends us messages after it sees data.
			Assert.assertTrue(null != _handler);
			if (VideoProcessContainer.COMMAND_CANCEL_PROCESSING.equals(message))
			{
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
				_handler = null;
			}
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			Assert.assertTrue(null == _handler);
			VideoProcessorCallbackHandler handler = new VideoProcessorCallbackHandler(session);
			if (_videoProcessContainer.attachListener(handler, _draftId))
			{
				_handler = handler;
			}
			else
			{
				// There is no active process so just close this.
				session.close(WebSocketCodes.NOT_FOUND, "No process running");
			}
		}
	}
}
