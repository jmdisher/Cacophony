package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.utils.Assert;


public class LocalActions
{
	private static final String INDEX_FILE = "index.dat";

	private final File _directory;

	public LocalActions(File directory)
	{
		_directory = directory;
	}

	public LocalIndex readIndex()
	{
		File indexFile = new File(_directory, INDEX_FILE);
		LocalIndex index = null;
		try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(indexFile)))
		{
			try
			{
				index = (LocalIndex) stream.readObject();
			}
			catch (ClassNotFoundException e)
			{
				throw Assert.unexpected(e);
			}
		}
		catch (FileNotFoundException e)
		{
			// This is acceptable and we just return null - means there is no config.
			index = null;
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		return index;
	}

	public void storeIndex(LocalIndex index)
	{
		File indexFile = new File(_directory, INDEX_FILE);
		try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(indexFile)))
		{
			stream.writeObject(index);
		}
		catch (FileNotFoundException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
	}
}
