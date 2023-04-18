package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;


public class MemoryConfigFileSystem implements IConfigFileSystem
{
	private final File _draftsDirectoryOrNull;
	private Map<String, byte[]> _data;

	public MemoryConfigFileSystem(File draftsDirectoryOrNull)
	{
		_draftsDirectoryOrNull = draftsDirectoryOrNull;
		if (null != _draftsDirectoryOrNull)
		{
			Assert.assertTrue(_draftsDirectoryOrNull.isDirectory());
		}
	}

	@Override
	public boolean createConfigDirectory()
	{
		boolean doesExist = (null != _data);
		if (!doesExist)
		{
			_data = new HashMap<>();
			// We want to sleeze in our reduced size default testing prefs, here.
			PrefsData.DEFAULT_FOLLOW_CACHE_BYTES = 100L;
		}
		return !doesExist;
	}

	@Override
	public boolean doesConfigDirectoryExist()
	{
		return (null != _data);
	}

	@Override
	public byte[] readTrivialFile(String fileName)
	{
		return _data.get(fileName);
	}

	@Override
	public void writeTrivialFile(String fileName, byte[] data)
	{
		_data.put(fileName, data);
	}

	@Override
	public InputStream readAtomicFile(String fileName)
	{
		// Note that the cases where we are using an existing config directory aren't explicitly called out yet so they may originate here.
		if (null == _data)
		{
			_data = new HashMap<>();
		}
		byte[] bytes = _data.get(fileName);
		return (null != bytes)
				? new ByteArrayInputStream(bytes)
				: null
		;
	}

	@Override
	public IConfigFileSystem.AtomicOutputStream writeAtomicFile(String fileName)
	{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		OutputStream outputStream = new OutputStream()
		{
			@Override
			public void write(int arg0) throws IOException
			{
				stream.write(arg0);
			}
			@Override
			public void close() throws IOException
			{
				stream.close();
				_data.put(fileName, stream.toByteArray());
			}
		};
		return new IConfigFileSystem.AtomicOutputStream()
		{
			private boolean _didCommit = false;
			@Override
			public void close() throws IOException
			{
				outputStream.close();
				if (_didCommit)
				{
					_data.put(fileName, stream.toByteArray());
				}
			}
			@Override
			public OutputStream getStream()
			{
				return outputStream;
			}
			@Override
			public void commit()
			{
				_didCommit = true;
			}
		};
	}

	@Override
	public File getDraftsTopLevelDirectory() throws IOException
	{
		// If we didn't give this a draft directory, we don't expect this method to be called.
		Assert.assertTrue(null != _draftsDirectoryOrNull);
		return _draftsDirectoryOrNull;
	}
}
