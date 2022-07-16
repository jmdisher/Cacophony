package com.jeffdisher.cacophony.interactive;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.logic.ExternalStreamProcessor;


public class VideoProcessor
{
	private DraftWrapper _wrapper;
	private ExternalStreamProcessor _processor;

	public VideoProcessor(ProcessWriter session, DraftManager manager, int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		_wrapper = manager.openExistingDraft(draftId);
		FileInputStream input = _wrapper.readOriginalVideo();
		FileOutputStream output = _wrapper.writeProcessedVideo();
		_processor = new ExternalStreamProcessor(processCommand);
		Consumer<Long> progressCallback = (Long sizeBytes) -> {
			session.totalBytesProcessed(sizeBytes);
		};
		Consumer<String> errorCallback = (String error) -> {
			session.processingError(error);
		};
		Runnable doneCallback = () -> {
			session.processingDone();
		};
		_processor.start(input, output, progressCallback, errorCallback, doneCallback);
	}

	public void sockedDidClose()
	{
		long processedSizeBytes = _processor.stop();
		if (processedSizeBytes >= 0L)
		{
			Draft oldDraft = _wrapper.loadDraft();
			SizedElement original = oldDraft.originalVideo();
			SizedElement processed = new SizedElement(original.mime(), original.height(), original.width(), processedSizeBytes);
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.thumbnail(), oldDraft.originalVideo(), processed);
			_wrapper.saveDraft(newDraft);
		}
	}


	public static interface ProcessWriter
	{
		void totalBytesProcessed(long bytesProcessed);
		void processingError(String error);
		void processingDone();
	}
}
