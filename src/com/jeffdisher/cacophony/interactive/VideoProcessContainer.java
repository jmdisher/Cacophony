package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.interactive.HandoffConnector.IHandoffListener;
import com.jeffdisher.cacophony.logic.DraftManager;


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

	private final DraftManager _manager;
	private final HandoffConnector<String, Long> _connector;
	private int _draftId;
	private VideoProcessor _processor;

	public VideoProcessContainer(DraftManager manager)
	{
		_manager = manager;
		_connector = new HandoffConnector<>();
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


	private synchronized void _clearProcess()
	{
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
			_clearProcess();
		}
	}
}
