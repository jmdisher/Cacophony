package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.ExternalStreamProcessor;
import com.jeffdisher.cacophony.logic.IDraftWrapper;


/**
 * A high-level wrapper over video post-processing functionality.
 */
public class VideoProcessor
{
	private IDraftWrapper _wrapper;
	private ExternalStreamProcessor _processor;

	/**
	 * Creates a video processor and starts it.  Note that the background operation is asynchronous and callbacks
	 * arrive on a background thread so it is possible for those callbacks to appear before this method has returned.
	 * 
	 * @param progressListener A listener receiving progress callbacks on background threads.
	 * @param manager The draft manager to consult for accessing the input and output files.
	 * @param draftId The draft ID upon which to operate.
	 * @param processCommand The processing command to run in a background process.
	 * @throws FileNotFoundException If the draft doesn't exist.
	 * @throws IOException If the background process fails to start.
	 */
	public VideoProcessor(ProcessWriter progressListener, DraftManager manager, int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		_wrapper = manager.openExistingDraft(draftId);
		if (null == _wrapper)
		{
			throw new FileNotFoundException();
		}
		InputStream input = _wrapper.readOriginalVideo();
		if (null == input)
		{
			throw new FileNotFoundException();
		}
		OutputStream output = _wrapper.writeProcessedVideo();
		_processor = new ExternalStreamProcessor(processCommand);
		Consumer<Long> progressCallback = (Long sizeBytes) -> {
			progressListener.totalBytesProcessed(sizeBytes);
		};
		Consumer<String> errorCallback = (String error) -> {
			progressListener.processingError(error);
		};
		Consumer<Long> doneCallback = (Long outputSizeBytes) -> {
			progressListener.processingDone(outputSizeBytes);
		};
		_processor.start(input, output, progressCallback, errorCallback, doneCallback);
	}

	/**
	 * Called to stop the video processing operation (or acknowledge that it has completed) when the socket monitoring
	 * the processing operation has been closed.
	 * Has the side-effect of writing-back the updated draft sizing data.
	 */
	public void sockedDidClose()
	{
		long processedSizeBytes = _processor.stop();
		if (processedSizeBytes >= 0L)
		{
			Draft oldDraft = _wrapper.loadDraft();
			SizedElement original = oldDraft.originalVideo();
			SizedElement processed = new SizedElement(original.mime(), original.height(), original.width(), processedSizeBytes);
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), processed, oldDraft.audio());
			_wrapper.saveDraft(newDraft);
		}
	}


	/**
	 * Callback interface which must be implemented by a listener to observe the processing progress.
	 * Note that these callbacks are made on a background thread but the callbacks will never be concurrently issued.
	 */
	public static interface ProcessWriter
	{
		/**
		 * Notified that a certain number of input bytes have been sent for processing.
		 * 
		 * @param bytesProcessed The total number of original video bytes which have now been sent for processing.
		 */
		void totalBytesProcessed(long bytesProcessed);
		/**
		 * A description of an error or error stream data coming from the background process.
		 * 
		 * @param error The error description.
		 */
		void processingError(String error);
		/**
		 * Called when the processing operation has completed successfully, returning the number of output bytes
		 * processed.
		 * NOTE:  This method returns "output" bytes while the processing progress callback uses "input" bytes.  This is
		 * because the total input to process is known when the operation is started, so the callback can be used to
		 * infer progress whereas the output bytes is useful to describe the total benefit of the post-processing.
		 * 
		 * @param outputSizeBytes The total size of the output video.
		 */
		void processingDone(long outputSizeBytes);
	}
}
