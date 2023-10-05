package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.logic.HandoffConnector;
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

	/**
	 * Creates the shared container for video processing.
	 * 
	 * @param manager 
	 * @param connector
	 */
	public VideoProcessContainer(DraftManager manager, HandoffConnector<String, Long> connector)
	{
		_manager = manager;
		_connector = connector;
	}

	/**
	 * Starts the video process.
	 * 
	 * @param draftId The draft to open.
	 * @param processCommand The command to run to process the video.
	 * @return True if the command did start, false if there is already a process running.
	 * @throws IOException The process failed to start.
	 */
	public synchronized boolean startProcess(int draftId, String processCommand) throws IOException
	{
		boolean didStart = false;
		if (null == _processor)
		{
			_draftId = draftId;
			// We want to create the KEY_INPUT_BYTES before the process starts but it might also fail in starting the process so handle that case and delete it, in that case.
			_connector.create(KEY_INPUT_BYTES, 0L);
			try
			{
				_processor = InteractiveHelpers.openVideoProcessor(new Handler(), _manager, _draftId, processCommand);
			}
			catch (IOException e)
			{
				// This is a failed start, so clear the input from the connector (avoids duplicate create on second attempt).
				_connector.destroy(KEY_INPUT_BYTES);
				throw e;
			}
			didStart = true;
		}
		return didStart;
	}

	/**
	 * Attaches a new listener to the current processing.
	 * 
	 * @param listener The listener to attach.
	 * @param draftId The draft to which it should be attached.
	 * @return True if the process was running for this draft, false if it wasn't and the listener was NOT attached.
	 */
	public synchronized boolean attachListener(HandoffConnector.IHandoffListener<String, Long> listener, int draftId)
	{
		boolean didConnect = false;
		if ((null != _processor) && (_draftId == draftId))
		{
			_connector.registerListener(listener, 0);
			didConnect = true;
		}
		return didConnect;
	}

	/**
	 * Detaches a previously attached listener.
	 * 
	 * @param listener The listener to detach.
	 */
	public synchronized void detachListener(HandoffConnector.IHandoffListener<String, Long> listener)
	{
		_connector.unregisterListener(listener);
	}

	/**
	 * Requests that the current process be cancelled.  This will return once the process has stopped.
	 */
	public void cancelProcess()
	{
		// This could have been asynchronously completed, since it is called from the processor callback and from the
		// socket, so check that.
		// NOTE:  The cancel operation can take some time, and can create hidden deadlocks since it waits on thread join
		// so we just unregister the processor variable under lock and run the close outside.
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
				// Theoretically, some kind of race condition could cause a failure here but this has never been
				// observed and would likely fail elsewhere.
				Assert.assertTrue(didDelete);
			}
			catch (FileNotFoundException e)
			{
				// For us to get to this point, it must be there.
				// Theoretically, some kind of race condition could cause a failure here but this has never been
				// observed and would likely fail elsewhere.
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
