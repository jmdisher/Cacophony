package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Cacophony is currently set to limit the number of concurrent video processing operations to 1.  This container
 * manages that process.
 */
public class VideoProcessContainer
{
	// Since the connector is associated with only a single draft ID, we can use the event "key" to describe progress
	// (input bytes) versus completion (output bytes).
	public static final String KEY_INPUT_BYTES = "inputBytes";
	public static final String KEY_OUTPUT_BYTES = "outputBytes";

	// We put the common command name here since anyone listening for it already references this.
	public static final String COMMAND_CANCEL_PROCESSING = "COMMAND_CANCEL_PROCESSING";

	private final DraftManager _manager;
	private final HandoffConnector<String, Long> _connector;
	private int _draftId;
	private VideoProcessor _processor;

	public VideoProcessContainer(DraftManager manager, HandoffConnector<String, Long> connector)
	{
		_manager = manager;
		_connector = connector;
	}

	public synchronized boolean startProcess(int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		boolean didStart = false;
		if (null == _processor)
		{
			_draftId = draftId;
			_connector.create(KEY_INPUT_BYTES, 0L);
			_processor = InteractiveHelpers.openVideoProcessor(new Handler(), _manager, _draftId, processCommand);
			didStart = true;
		}
		return didStart;
	}

	public synchronized boolean attachListener(IHandoffListener<String, Long> listener, int draftId)
	{
		boolean didConnect = false;
		if ((null != _processor) && (_draftId == draftId))
		{
			_connector.registerListener(listener);
			didConnect = true;
		}
		return didConnect;
	}

	public synchronized void detachListener(IHandoffListener<String, Long> listener)
	{
		_connector.unregisterListener(listener);
	}

	public void cancelProcess()
	{
		// This could have been asynchronously completed, since it is called from the processor callback and from the
		// socket, so check that.
		// NOTE:  The cancel operation can take some time, and can create hidden deadlocks since it waits on thread join
		// so we just unregister the processor vaiable under lock and run the close outside.
		VideoProcessor processorToClose = null;
		synchronized(this)
		{
			if (null != _processor)
			{
				processorToClose = _processor;
				// _processor will be cleared in the _clearProcess callback.
			}
		}
		if (null != processorToClose)
		{
			InteractiveHelpers.closeVideoProcessor(processorToClose);
		}
	}


	private synchronized void _clearProcess(boolean deleteVideo)
	{
		// If the video processing failed or was cancelled, we want to delete the video.
		if (deleteVideo)
		{
			try
			{
				boolean didDelete = InteractiveHelpers.deleteProcessedVideo(_manager, _draftId);
				// For us to get to this point, it must be there.
				// TODO: Verify that a race condition couldn't cause this to fail for safe reason.
				Assert.assertTrue(didDelete);
			}
			catch (FileNotFoundException e)
			{
				// For us to get to this point, it must be there.
				// TODO: Verify that a race condition couldn't cause this to fail for safe reason.
				throw Assert.unexpected(e);
			}
		}
		_processor = null;
		_draftId = 0;
	}


	private class Handler implements VideoProcessor.ProcessWriter
	{
		@Override
		public void totalBytesProcessed(long bytesProcessed)
		{
			_connector.update(KEY_INPUT_BYTES, bytesProcessed);
		}
		@Override
		public void processingError(String error)
		{
			// We aren't interested in the error data since this is usually just informational.
		}
		@Override
		public void processingDone(long outputSizeBytes)
		{
			// destroy the input key, then create and destroy the output key.
			_connector.destroy(KEY_INPUT_BYTES);
			_connector.create(KEY_OUTPUT_BYTES, outputSizeBytes);
			_connector.destroy(KEY_OUTPUT_BYTES);
			// We know that a -1 byte size means "cancel" or "error".
			boolean shouldDelete = (-1L == outputSizeBytes);
			_clearProcess(shouldDelete);
		}
	}
}
