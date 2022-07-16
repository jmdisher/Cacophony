package com.jeffdisher.cacophony.logic;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.utils.Assert;


public class DraftManager
{
	private static final String DIRECTORY_PREFIX = "draft_";

	private final IConfigFileSystem _fileSystem;

	public DraftManager(IConfigFileSystem fileSystem)
	{
		_fileSystem = fileSystem;
	}

	public DraftWrapper openExistingDraft(int id) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		BasicDirectory directory = _fileSystem.openDirectoryWithName(DIRECTORY_PREFIX + id);
		return new DraftWrapper(directory);
	}

	public DraftWrapper createNewDraft(int id) throws IOException
	{
		Assert.assertTrue(id > 0);
		Draft draft = new Draft(id, 0L, "New Draft - " + id, "No description", null, null, null);
		BasicDirectory directory = _fileSystem.createDirectoryWithName(DIRECTORY_PREFIX + id);
		DraftWrapper wrapper = new DraftWrapper(directory);
		wrapper.saveDraft(draft);
		return wrapper;
	}

	public void deleteExistingDraft(int id) throws FileNotFoundException
	{
		Assert.assertTrue(id > 0);
		_fileSystem.recursiveDeleteDirectoryWithName(DIRECTORY_PREFIX + id);
	}

	public List<Draft> listAllDrafts()
	{
		List<BasicDirectory> directories = _fileSystem.listDirectoriesWithPrefix(DIRECTORY_PREFIX);
		return directories.stream().map(
				(BasicDirectory oneDir) -> new DraftWrapper(oneDir).loadDraft()
		).collect(Collectors.toList());
	}
}
