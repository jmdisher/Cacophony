package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IDraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Opens an audio save web socket for the given draft ID.
 * Receives binary payloads from the client which are interpreted as the binary stream for the audio.
 * Sends nothing.
 */
public class WS_DraftSaveAudio implements ValidatedEntryPoints.WEB_SOCKET_FACTORY
{
	private final DraftManager _draftManager;
	
	public WS_DraftSaveAudio(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public WebSocketListener build(Object[] path)
	{
		int draftId = (Integer)path[3];
		// Since we know everything coming through this path is an "audio/" mime type, we just pass the second part in the path to avoid having to reencode it.
		String codec = (String)path[4];
		String mime = "audio/" + codec;
		return new SaveAudioWebSocketListener(_draftManager, draftId, mime);
	}


	private static class SaveAudioWebSocketListener implements WebSocketListener
	{
		private final DraftManager _draftManager;
		private final int _draftId;
		private final String _mime;
		private IDraftWrapper _openDraft;
		private OutputStream _outputStream;
		private long _bytesSavedToStream;
		
		public SaveAudioWebSocketListener(DraftManager draftManager, int draftId, String mime)
		{
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
				session.close(WebSocketCodes.NOT_FOUND, "Draft does not exist");
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
