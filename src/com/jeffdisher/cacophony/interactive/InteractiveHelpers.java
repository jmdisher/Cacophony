package com.jeffdisher.cacophony.interactive;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * These are very simple helpers used by the InteractiveServer, only pulled out that they can be understood and tested
 * in a high-level way, without needing to also test the REST server at the same time.
 */
public class InteractiveHelpers
{
	// --- Methods related to saving the new video.
	public static VideoSaver openNewVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return new VideoSaver(draftManager, draftId);
	}
	public static void appendToNewVideo(VideoSaver saver, byte[] payload, int offset, int len)
	{
		saver.append(payload, offset, len);
	}
	public static void closeNewVideo(VideoSaver saver, String mime, int height, int width)
	{
		long savedFileSizeBytes = saver.sockedDidClose();
		
		// We now update the final state.
		Draft oldDraft = saver.draftWrapper.loadDraft();
		SizedElement originalVideo = new SizedElement(mime, height, width, savedFileSizeBytes);
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.thumbnail(), originalVideo, oldDraft.processedVideo());
		saver.draftWrapper.saveDraft(newDraft);
	}

	// --- Methods related to processing the video (this is small since it mostly just invokes callbacks to the session on a different thread).
	public static VideoProcessor openVideoProcessor(VideoProcessor.ProcessWriter session, DraftManager draftManager, int draftId, String processCommand) throws FileNotFoundException, IOException
	{
		return new VideoProcessor(session, draftManager, draftId, processCommand);
	}
	public static void closeVideoProcessor(VideoProcessor processor)
	{
		processor.sockedDidClose();
	}

	// --- Methods related to draft management.
	public static List<Draft> listDrafts(DraftManager draftManager)
	{
		return draftManager.listAllDrafts();
	}
	public static Draft createNewDraft(DraftManager draftManager, int draftId) throws IOException
	{
		return draftManager.createNewDraft(draftId).loadDraft();
	}
	public static Draft readExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return draftManager.openExistingDraft(draftId).loadDraft();
	}
	public static Draft updateDraftTitle(DraftManager draftManager, int draftId, String title, String description) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft oldDraft = wrapper.loadDraft();
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), title, description, oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
		return newDraft;
	}
	public static void deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		draftManager.deleteExistingDraft(draftId);
	}
	public static void publishExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		// TODO: Generalize the publish logic so we can call it here.
		throw Assert.unimplemented(2);
	}

	// --- Methods related to thumbnails.
	public static void loadThumbnailToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileInputStream input = wrapper.readThumbnail())
		{
			mimeConsumer.accept("image/jpeg");
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void saveThumbnailFromStream(DraftManager draftManager, int draftId, InputStream inStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileOutputStream output = wrapper.writeThumbnail())
		{
			_copyToEndOfFile(inStream, output);
		}
	}

	// --- Methods related to video streaming.
	public static void writeOriginalVideoToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileInputStream input = wrapper.readOriginalVideo())
		{
			mimeConsumer.accept(wrapper.loadDraft().originalVideo().mime());
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void writeProcessedVideoToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileInputStream input = wrapper.readProcessedVideo())
		{
			mimeConsumer.accept("video/webm");
			_copyToEndOfFile(input, outStream);
		}
	}


	private static void _copyToEndOfFile(InputStream input, OutputStream output) throws IOException
	{
		boolean reading = true;
		byte[] data = new byte[4096];
		while (reading)
		{
			int read = input.read(data);
			if (read > 0)
			{
				output.write(data, 0, read);
			}
			else
			{
				reading = false;
			}
		}
	}
}
