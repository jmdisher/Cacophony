package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import com.jeffdisher.breakwater.utilities.Assert;


public class BasicDirectory
{
	private final File _directory;

	public BasicDirectory(File directory)
	{
		_directory = directory;
	}

	public FileInputStream readFile(String name)
	{
		File file = new File(_directory, name);
		FileInputStream stream = null;
		try
		{
			stream = new FileInputStream(file);
		}
		catch (FileNotFoundException e)
		{
			// We should have already verified this exists.
			throw Assert.unexpected(e);
		}
		return stream;
	}

	public FileOutputStream writeFile(String name)
	{
		File file = new File(_directory, name);
		FileOutputStream stream = null;
		try
		{
			stream = new FileOutputStream(file);
		}
		catch (FileNotFoundException e)
		{
			// We should have already verified this exists.
			throw Assert.unexpected(e);
		}
		return stream;
	}
}
