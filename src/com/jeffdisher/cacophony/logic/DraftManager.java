package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The high-level abstraction over top of the individual Drafts, managed via DraftWrapper instances.
 * The instance is given a directory and it manages drafts within that.
 * Note that the manager tracks the long-lived DraftWrapper instances.  These instances are shared across callers since
 * they may be concurrently used and the wrapper provides the locking around high-level operations.
 * The manager itself has a synchronized public interface for the same reason - drafts can be concurrently manipulated.
 */
public class DraftManager
{
	private static final String DIRECTORY_PREFIX = "draft_";

	private final File _draftsDirectory;
	private final Map<Integer, DraftWrapper> _sharedWrappers;

	public DraftManager(File draftsDirectory)
	{
		Assert.assertTrue(null != draftsDirectory);
		
		_draftsDirectory = draftsDirectory;
		_sharedWrappers = new HashMap<>();
		
		_populateDrafts(_sharedWrappers, _draftsDirectory);
	}

	/**
	 * Reads an existing draft, returning a shared instance.
	 * 
	 * @param id The draft ID.
	 * @return The shared DraftWrapper instance for the given id or null if the draft is unknown.
	 */
	public synchronized IDraftWrapper openExistingDraft(int id)
	{
		Assert.assertTrue(id > 0);
		return _sharedWrappers.get(id);
	}

	/**
	 * Creates a new draft with the given ID and returns the shared instance.  Has the consequence of creating the draft
	 * with an initial empty state.
	 * Fails with an assertion if the draft already exists.
	 * 
	 * @param id The draft ID.
	 * @return The shared DraftWrapper instance for the given id..
	 */
	public synchronized IDraftWrapper createNewDraft(int id)
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		// Make sure the directory doesn't already exist.
		Assert.assertTrue(!subDir.exists());
		// Make sure that the creation is a success.
		Assert.assertTrue(subDir.mkdir());
		Draft draft = new Draft(id, 0L, "New Draft - " + id, "No description", "", null, null, null, null);
		DraftWrapper wrapper = new DraftWrapper(subDir);
		wrapper.saveDraft(draft);
		_sharedWrappers.put(id, wrapper);
		return wrapper;
	}

	/**
	 * Deletes an existing draft.
	 * 
	 * @param id The draft ID.
	 * @return True if the draft was deleted or false if the draft couldn't be deleted due to existing readers or
	 * writers.
	 * @throws FileNotFoundException The draft is unknown.
	 */
	public synchronized boolean deleteExistingDraft(int id) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		if (!subDir.isDirectory())
		{
			throw new FileNotFoundException();
		}
		
		// We can fail the delete if something here is still open.
		boolean didDelete = false;
		if (_sharedWrappers.get(id).deleteDraft())
		{
			_sharedWrappers.remove(id);
			didDelete = true;
		}
		return didDelete;
	}

	/**
	 * @return A list of all drafts currently known to the system, in no specific order.
	 */
	public synchronized List<Draft> listAllDrafts()
	{
		return _sharedWrappers.values().stream().map((DraftWrapper wrapper) -> wrapper.loadDraft()).collect(Collectors.toList());
	}

	/**
	 * This helper is only in place temporarily, to facilitate the data migration from V2 to V3, as this includes
	 * changing the Drafts, as well.
	 */
	public synchronized void migrateDrafts()
	{
		for (DraftWrapper wrapper : _sharedWrappers.values())
		{
			wrapper.migrateDraft();
		}
	}

	/**
	 * Returns the ready-to-run command to publish the draft.  This operation is read-only and doesn't change the draft.
	 * This may be removed/changed in the future as it is oddly specific to this one application, but avoids adding lots
	 * of other information to the draft interfaces in order to implement this externally.
	 * 
	 * @param id The draft ID.
	 * @param shouldPublishVideo True if we should publish video.
	 * @param shouldPublishAudio True if we should publish audio.
	 * @return The command which can be run to publish the draft.
	 * @throws FileNotFoundException The draft is unknown.
	 */
	public synchronized PublishCommand prepareToPublishDraft(int id
			, boolean shouldPublishVideo
			, boolean shouldPublishAudio
	) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		DraftWrapper wrapper = _sharedWrappers.get(id);
		if (null == wrapper)
		{
			throw new FileNotFoundException("Unknown draft: " + id);
		}
		
		// We want to look up the required file attachments.
		Draft draft = wrapper.loadDraft();
		SizedElement video = null;
		File videoFile = null;
		if (shouldPublishVideo)
		{
			video = draft.processedVideo();
			videoFile = wrapper.existingProcessedVideoFile();
			if (null == video)
			{
				Assert.assertTrue(null == videoFile);
				video = draft.originalVideo();
				videoFile = wrapper.existingOriginalVideoFile();
			}
		}
		SizedElement audio = null;
		File audioFile = null;
		if (shouldPublishAudio)
		{
			audio = draft.audio();
			audioFile = wrapper.existingAudioFile();
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
		if (null != audio)
		{
			elementCount += 1;
		}
		ElementSubCommand[] subElements = new ElementSubCommand[elementCount];
		int index = 0;
		if (null != thumbnail)
		{
			File thumbnailFile = wrapper.existingThumbnailFile();
			Assert.assertTrue(null != thumbnailFile);
			// Note that the dimensions are technically only used by the video but we will attach them here, anyway.
			subElements[index] = new ElementSubCommand(thumbnail.mime(), thumbnailFile, thumbnail.height(), thumbnail.width(), true);
			index += 1;
		}
		if (null != video)
		{
			Assert.assertTrue(null != videoFile);
			subElements[index] = new ElementSubCommand(video.mime(), videoFile, video.height(), video.width(), false);
			index += 1;
		}
		if (null != audio)
		{
			Assert.assertTrue(null != audioFile);
			subElements[index] = new ElementSubCommand(audio.mime(), audioFile, audio.height(), audio.width(), false);
			index += 1;
		}
		
		String name = draft.title();
		String description = draft.description();
		String discussionUrl = draft.discussionUrl();
		return new PublishCommand(name, description, discussionUrl, subElements);
	}


	private static void _populateDrafts(Map<Integer, DraftWrapper> container, File draftsDirectory)
	{
		File[] files = draftsDirectory.listFiles((File dir, String name) -> name.startsWith(DIRECTORY_PREFIX));
		for (File dir : files)
		{
			int id = Integer.parseInt(dir.getName().substring(DIRECTORY_PREFIX.length()));
			DraftWrapper wrapper = new DraftWrapper(dir);
			container.put(id, wrapper);
		}
	}
}
