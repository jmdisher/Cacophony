package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jeffdisher.cacophony.utils.Assert;


public class RealConfigFileSystem implements IConfigFileSystem
{
	private static final String DRAFTS_DIRECTORY_NAME = "drafts";

	private final File _directory;

	public RealConfigFileSystem(File directory)
	{
		_directory = directory;
	}

	@Override
	public boolean createConfigDirectory()
	{
		// If the directory doesn't exist, or does exist and is empty, we consider this a "success".
		_directory.mkdirs();
		return (0 == _directory.list().length);
	}

	@Override
	public boolean doesConfigDirectoryExist()
	{
		return _directory.isDirectory();
	}

	@Override
	public InputStream readConfigFile(String fileName)
	{
		File file = new File(_directory, fileName);
		FileInputStream stream = null;
		try
		{
			stream = new FileInputStream(file);
		}
		catch (FileNotFoundException e)
		{
			// In this case, we just return null.
			stream = null;
		}
		return stream;
	}

	@Override
	public OutputStream writeConfigFile(String fileName)
	{
		File file = new File(_directory, fileName);
		try
		{
			return new FileOutputStream(file);
		}
		catch (FileNotFoundException e)
		{
			// We don't expect this here since we already verified the directory exists.
			throw Assert.unexpected(e);
		}
	}

	@Override
	public File getDraftsTopLevelDirectory() throws IOException
	{
		File draftsDirectory = new File(_directory, DRAFTS_DIRECTORY_NAME);
		// Make sure that the directory exists.
		if (!draftsDirectory.isDirectory())
		{
			boolean didMake = draftsDirectory.mkdirs();
			if (!didMake)
			{
				throw new IOException("Failed to create directory: " + draftsDirectory);
			}
		}
		// We are going to assume that the wrong file type is just a corrupt config.
		Assert.assertTrue(draftsDirectory.isDirectory());
		return draftsDirectory;
	}
}
