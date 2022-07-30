package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A wrapper over the basic functionality for writing the original video file.  It opens the output stream upon
 * creation, closes it when the socket closes, and allows appending bytes.
 */
public class VideoSaver
{
	private final FileOutputStream _stream;
	private long _byteSize;

	// This is public just because it is associated with the video and it is needed when closing the video to update the draft.
	public final DraftWrapper draftWrapper;

	public VideoSaver(DraftManager manager, int draftId) throws FileNotFoundException
	{
		this.draftWrapper = manager.openExistingDraft(draftId);
		_stream = new FileOutputStream(this.draftWrapper.originalVideo());
	}

	/**
	 * Closes the underlying stream and returns the number of bytes written.
	 * 
	 * @return The number of bytes written to the file, in total.
	 */
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

	/**
	 * Appends the described segment of the given payload to the underlying file.
	 * 
	 * @param payload The bytes to write.
	 * @param offset Offset into the payload from which the write should begin.
	 * @param len The number of bytes to write from payload.
	 */
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
