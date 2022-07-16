package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


public class VideoSaver
{
	private final FileOutputStream _stream;
	private long _byteSize;

	// This is public just because it is associated with the video and it is needed when closing the video to update the draft.
	public final DraftWrapper draftWrapper;

	public VideoSaver(DraftManager manager, int draftId) throws FileNotFoundException
	{
		this.draftWrapper = manager.openExistingDraft(draftId);
		_stream = this.draftWrapper.writeOriginalVideo();
	}

	public long sockedDidClose()
	{
		try
		{
			_stream.close();
		}
		catch (IOException e)
		{
			// Not sure how this would happen.
			throw Assert.unexpected(e);
		}
		return _byteSize;
	}

	public void append(byte[] payload, int offset, int len)
	{
		try
		{
			_stream.write(payload, offset, len);
		}
		catch (IOException e)
		{
			// We don't expect this to happen, normally.
			throw Assert.unexpected(e);
		}
		_byteSize += len;
	}
}
