package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Opens a video save web socket for the given draft ID.
 */
public class WS_DraftSaveVideo implements IWebSocketFactory
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public WS_DraftSaveVideo(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		int draftId = Integer.parseInt(variables[0]);
		int height = Integer.parseInt(variables[1]);
		int width = Integer.parseInt(variables[2]);
		// Since we know everything coming through this path is an "video/" mime type, we just pass the second part in the path to avoid having to reencode it.
		String codec = variables[3];
		String mime = "video/" + codec;
		return new SaveVideoWebSocketListener(_xsrf, _draftManager, draftId, height, width, mime);
	}


	private static class SaveVideoWebSocketListener implements WebSocketListener
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final int _draftId;
		private final int _height;
		private final int _width;
		private final String _mime;
		private DraftWrapper _openDraft;
		private FileOutputStream _outputStream;
		private long _bytesSavedToStream;
		
		public SaveVideoWebSocketListener(String xsrf, DraftManager draftManager, int draftId, int height, int width, String mime)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_draftId = draftId;
			_height = height;
			_width = width;
			_mime = mime;
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			Assert.assertTrue(null != _outputStream);
			try
			{
				_outputStream.close();
			}
			catch (IOException e)
			{
				// Not expected here.
				throw Assert.unexpected(e);
			}
			InteractiveHelpers.updateOriginalVideo(_openDraft, _mime, _height, _width, _bytesSavedToStream);
			_openDraft = null;
			_outputStream = null;
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (InteractiveHelpers.verifySafeWebSocket(_xsrf, session))
			{
				// 256 KiB should be reasonable.
				session.setMaxBinaryMessageSize(256 * 1024);
				Assert.assertTrue(null == _outputStream);
				try
				{
					_openDraft = _draftManager.openExistingDraft(_draftId);
					_outputStream = new FileOutputStream(_openDraft.originalVideo());
					_bytesSavedToStream = 0;
				}
				catch (FileNotFoundException e)
				{
					// This happens in the case where the draft doesn't exist.
					session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
				}
			}
		}
		
		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len)
		{
			Assert.assertTrue(null != _outputStream);
			try
			{
				_outputStream.write(payload, offset, len);
				_bytesSavedToStream += len;
			}
			catch (IOException e)
			{
				// TODO:  Determine how we want to handle this failure if we see it happen.
				throw Assert.unexpected(e);
			}
		}
	}
}
