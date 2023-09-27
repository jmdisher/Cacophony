package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.data.local.v4.SizedElement;
import com.jeffdisher.cacophony.types.IpfsFile;
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
	 * @param replyTo Null, or the CID of a stream to which we are replying.
	 * @return The shared DraftWrapper instance for the given id..
	 */
	public synchronized IDraftWrapper createNewDraft(int id, IpfsFile replyTo)
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		// Make sure the directory doesn't already exist.
		Assert.assertTrue(!subDir.exists());
		// Make sure that the creation is a success.
		Assert.assertTrue(subDir.mkdir());
		Draft draft = new Draft(id, 0L, "New Draft - " + id, "No description", "", null, null, null, null, replyTo);
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
	 * Populates the given IPublishBuilder with the information about attachments and other content required to publish
	 * the draft to the channel.
	 * This operation is read-only and doesn't change the draft.
	 * 
	 * @param builder The publish command builder.
	 * @param id The draft ID.
	 * @param shouldPublishVideo True if we should publish video.
	 * @param shouldPublishAudio True if we should publish audio.
	 * @throws FileNotFoundException The draft is unknown.
	 */
	public synchronized void prepareToPublishDraft(IPublishBuilder builder
			, int id
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
			if (null != video)
			{
				builder.attach(video.mime(), videoFile, video.height(), video.width());
			}
		}
		SizedElement audio = null;
		File audioFile = null;
		if (shouldPublishAudio)
		{
			audio = draft.audio();
			audioFile = wrapper.existingAudioFile();
			if (null != audio)
			{
				builder.attach(audio.mime(), audioFile, audio.height(), audio.width());
			}
		}
		
		String name = draft.title();
		String description = draft.description();
		String discussionUrl = draft.discussionUrl();
		IpfsFile replyTo = draft.replyTo();
		SizedElement thumbnail = draft.thumbnail();
		String thumbnailMime = (null != thumbnail)
				? thumbnail.mime()
				: null
		;
		File thumbnailFile = (null != thumbnail)
				? wrapper.existingThumbnailFile()
				: null
		;
		builder.complete(name, description, discussionUrl, replyTo, thumbnailMime, thumbnailFile);
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


	/**
	 * The interface used for the DraftManager to request that a publish command be created for one of its drafts.
	 * The purpose of this is that this relatively low-level class doesn't know directly about the much higher-level
	 * Command system.
	 */
	public static interface IPublishBuilder
	{
		/**
		 * Attaches a file to the publish.
		 * 
		 * @param mime MIME type of the data.
		 * @param filePath The location of the file.
		 * @param height The height of the attachment (0 if not relevant).
		 * @param width The width of the attachment (0 if not relevant).
		 */
		void attach(String mime, File filePath, int height, int width);
		/**
		 * Called to finalize the creation of the publish command.  This is expected to only be called once and only
		 * after any attachments are provided.
		 * 
		 * @param name The name of the new post.
		 * @param description The description of the new post.
		 * @param discussionUrl A reference to an external discussion (can be null).
		 * @param replyTo Another post CID to which this one is a reply (can be null).
		 * @param thumbnailMime The MIME of the thumbnail (null if not present).
		 * @param thumbnailPath The location of the thumbnail image (null if not present).
		 */
		void complete(String name, String description, String discussionUrl, IpfsFile replyTo, String thumbnailMime, File thumbnailPath);
	}
}
