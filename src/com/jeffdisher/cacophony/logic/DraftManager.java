package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.local.v1.Draft;
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
