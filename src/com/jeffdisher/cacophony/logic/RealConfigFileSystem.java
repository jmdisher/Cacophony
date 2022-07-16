package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jeffdisher.cacophony.utils.Assert;


public class RealConfigFileSystem implements IConfigFileSystem
{
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
	public String getDirectoryForReporting()
	{
		return _directory.getAbsolutePath();
	}

	@Override
	public BasicDirectory createDirectoryWithName(String directoryName) throws IOException
	{
		File subDir = new File(_directory, directoryName);
		// Make sure the directory doesn't already exist.
		Assert.assertTrue(!subDir.exists());
		// Make sure that the creation is a success.
		Assert.assertTrue(subDir.mkdir());
		return new BasicDirectory(subDir);
	}

	@Override
	public BasicDirectory openDirectoryWithName(String directoryName) throws FileNotFoundException
	{
		File subDir = new File(_directory, directoryName);
		// Make sure that this already exists and is a directory.
		if (!subDir.isDirectory())
		{
			throw new FileNotFoundException("Directory does not exist: " + subDir);
		}
		return new BasicDirectory(subDir);
	}

	@Override
	public List<BasicDirectory> listDirectoriesWithPrefix(String directoryPrefix)
	{
		File[] files = _directory.listFiles((File dir, String name) -> name.startsWith(directoryPrefix));
		return Stream.of(files).map((File dir) -> new BasicDirectory(dir)).collect(Collectors.toList());
	}

	@Override
	public void recursiveDeleteDirectoryWithName(String directoryName) throws FileNotFoundException
	{
		File subDir = new File(_directory, directoryName);
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
}
