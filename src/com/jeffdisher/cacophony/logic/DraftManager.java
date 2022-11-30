package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The high-level abstraction over top of the individual Drafts, managed via DraftWrapper instances.
 * The instance is given a directory and it manages drafts within that.
 */
public class DraftManager
{
	private static final String DIRECTORY_PREFIX = "draft_";

	private final File _draftsDirectory;

	public DraftManager(File draftsDirectory)
	{
		_draftsDirectory = draftsDirectory;
	}

	public DraftWrapper openExistingDraft(int id) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		// Make sure that this already exists and is a directory.
		if (!subDir.isDirectory())
		{
			throw new FileNotFoundException("Directory does not exist: " + subDir);
		}
		return new DraftWrapper(subDir);
	}

	public DraftWrapper createNewDraft(int id) throws IOException
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		// Make sure the directory doesn't already exist.
		Assert.assertTrue(!subDir.exists());
		// Make sure that the creation is a success.
		Assert.assertTrue(subDir.mkdir());
		Draft draft = new Draft(id, 0L, "New Draft - " + id, "No description", null, null, null, null, null);
		DraftWrapper wrapper = new DraftWrapper(subDir);
		wrapper.saveDraft(draft);
		return wrapper;
	}

	public void deleteExistingDraft(int id) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		String directoryName = DIRECTORY_PREFIX + id;
		File subDir = new File(_draftsDirectory, directoryName);
		if (!subDir.isDirectory())
		{
			throw new FileNotFoundException("Directory does not exist: " + subDir);
		}
		try
		{
			Files.walk(subDir.toPath())
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
		catch (IOException e)
		{
			// We already checked this existed so we don't expect other errors here.
			throw Assert.unexpected(e);
		}
	}

	public List<Draft> listAllDrafts()
	{
		File[] files = _draftsDirectory.listFiles((File dir, String name) -> name.startsWith(DIRECTORY_PREFIX));
		return Stream.of(files).map(
				(File oneDir) -> new DraftWrapper(oneDir).loadDraft()
		).collect(Collectors.toList());
	}
}
