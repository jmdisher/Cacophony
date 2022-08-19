package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), originalVideo, oldDraft.processedVideo());
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
	public static Draft updateDraftText(DraftManager draftManager, int draftId, String title, String description, String discussionUrl) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft oldDraft = wrapper.loadDraft();
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), title, description, discussionUrl, oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
		return newDraft;
	}
	public static void deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		draftManager.deleteExistingDraft(draftId);
	}
	public static void publishExistingDraft(IEnvironment environment, DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft draft = wrapper.loadDraft();
		SizedElement video = draft.processedVideo();
		File videoFile = wrapper.processedVideo();
		if (null == video)
		{
			video = draft.originalVideo();
			videoFile = wrapper.originalVideo();
		}
		SizedElement thumbnail = draft.thumbnail();
		int elementCount = 0;
		if (null != thumbnail)
		{
			elementCount += 1;
		}
		if (null != video)
		{
			elementCount += 1;
		}
		ElementSubCommand[] subElements = new ElementSubCommand[elementCount];
		int index = 0;
		if (null != thumbnail)
		{
			subElements[index] = new ElementSubCommand(thumbnail.mime(), wrapper.thumbnail(), thumbnail.height(), thumbnail.width(), true);
			index += 1;
		}
		if (null != video)
		{
			subElements[index] = new ElementSubCommand(video.mime(), videoFile, video.height(), video.width(), false);
			elementCount += 1;
		}
		
		PublishCommand command = new PublishCommand(draft.title(), draft.description(), draft.discussionUrl(), subElements);
		try
		{
			command.runInEnvironment(environment);
		}
		catch (IpfsConnectionException e)
		{
			System.err.println("Publish command failed with IpfsConnectionException: " + e.getLocalizedMessage());
			e.printStackTrace();
			throw Assert.unexpected(e);
		}
		catch (CacophonyException e)
		{
			System.err.println("Publish command failed with CacophonyException: " + e.getLocalizedMessage());
			e.printStackTrace();
			throw Assert.unexpected(e);
		}
	}

	// --- Methods related to thumbnails.
	public static void loadThumbnailToStream(DraftManager draftManager, int draftId, Consumer<String> mimeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		try (FileInputStream input = new FileInputStream(wrapper.thumbnail()))
		{
			mimeConsumer.accept("image/jpeg");
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void saveThumbnailFromStream(DraftManager draftManager, int draftId, int height, int width, InputStream inStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		long bytesCopied = 0L;
		try (FileOutputStream output = new FileOutputStream(wrapper.thumbnail()))
		{
			bytesCopied = _copyToEndOfFile(inStream, output);
		}
		Assert.assertTrue(bytesCopied > 0L);
		Draft oldDraft = wrapper.loadDraft();
		SizedElement thumbnail = new SizedElement("image/jpeg", height, width, bytesCopied);
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), thumbnail, oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
	}

	// --- Methods related to video streaming.
	public static void writeOriginalVideoToStream(DraftManager draftManager, int draftId, BiConsumer<String, Long> mimeSizeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File originalFile = wrapper.originalVideo();
		try (FileInputStream input = new FileInputStream(originalFile))
		{
			mimeSizeConsumer.accept(wrapper.loadDraft().originalVideo().mime(), originalFile.length());
			_copyToEndOfFile(input, outStream);
		}
	}
	public static void writeProcessedVideoToStream(DraftManager draftManager, int draftId, BiConsumer<String, Long> mimeSizeConsumer, OutputStream outStream) throws FileNotFoundException, IOException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File processedFile = wrapper.processedVideo();
		try (FileInputStream input = new FileInputStream(processedFile))
		{
			mimeSizeConsumer.accept("video/webm", processedFile.length());
			_copyToEndOfFile(input, outStream);
		}
	}

	// --- Methods related to deleting videos.
	// Note that we don't build any special interlock around the delete relative to other operations like processing.
	// This is because that would be heavy-weight and complicated for something which we can prevent in the front-end
	//  and it would only corrupt the draft in progress.
	// More complex protection could be added in the future if this turns out to be a problem but this keeps it simple.
	/**
	 * Deletes the original video from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no video file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteOriginalVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File originalFile = wrapper.originalVideo();
		boolean didDelete = originalFile.delete();
		if (didDelete)
		{
			Draft oldDraft = wrapper.loadDraft();
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), null, oldDraft.processedVideo());
			wrapper.saveDraft(newDraft);
		}
		return didDelete;
	}
	/**
	 * Deletes the processed video from the draft.
	 * @param draftManager The DraftManager.
	 * @param draftId The draft to open when searching for the video.
	 * @return True if this was deleted or false if it couldn't be or there was no video file.
	 * @throws FileNotFoundException The draft doesn't exist.
	 */
	public static boolean deleteProcessedVideo(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		File processedFile = wrapper.processedVideo();
		boolean didDelete = processedFile.delete();
		if (didDelete)
		{
			Draft oldDraft = wrapper.loadDraft();
			Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), oldDraft.title(), oldDraft.description(), oldDraft.discussionUrl(), oldDraft.thumbnail(), oldDraft.originalVideo(), null);
			wrapper.saveDraft(newDraft);
		}
		return didDelete;
	}


	private static long _copyToEndOfFile(InputStream input, OutputStream output) throws IOException
	{
		long totalCopied = 0L;
		boolean reading = true;
		byte[] data = new byte[4096];
		while (reading)
		{
			int read = input.read(data);
			if (read > 0)
			{
				output.write(data, 0, read);
				totalCopied += (long)read;
			}
			else
			{
				reading = false;
			}
		}
		return totalCopied;
	}
}
