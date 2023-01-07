package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Opens an audio save web socket for the given draft ID.
 */
public class WS_DraftSaveAudio implements IWebSocketFactory
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public WS_DraftSaveAudio(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public WebSocketListener create(String[] variables)
	{
		int draftId = Integer.parseInt(variables[0]);
		// Since we know everything coming through this path is an "audio/" mime type, we just pass the second part in the path to avoid having to reencode it.
		String codec = variables[1];
		String mime = "audio/" + codec;
		return new SaveAudioWebSocketListener(_xsrf, _draftManager, draftId, mime);
	}


	private static class SaveAudioWebSocketListener implements WebSocketListener
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final int _draftId;
		private final String _mime;
		private DraftWrapper _openDraft;
		private OutputStream _outputStream;
		private long _bytesSavedToStream;
		
		public SaveAudioWebSocketListener(String xsrf, DraftManager draftManager, int draftId, String mime)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_draftId = draftId;
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
			InteractiveHelpers.updateAudio(_openDraft, _mime, _bytesSavedToStream);
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
				_openDraft = _draftManager.openExistingDraft(_draftId);
				if (null != _openDraft)
				{
					_outputStream = _openDraft.writeAudio();
					_bytesSavedToStream = 0;
				}
				else
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
