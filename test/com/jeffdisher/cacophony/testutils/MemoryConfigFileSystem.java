package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.utils.Assert;


public class MemoryConfigFileSystem implements IConfigFileSystem
{
	private Map<String, byte[]> _data;

	@Override
	public boolean createConfigDirectory()
	{
		boolean doesExist = (null != _data);
		if (!doesExist)
		{
			_data = new HashMap<>();
			// We want to sleeze in our reduced size default testing prefs, here.
			_saveDefaultTestingPrefs();
		}
		return !doesExist;
	}

	@Override
	public InputStream readConfigFile(String fileName)
	{
		// Note that the cases where we are using an existing config directory aren't explicitly called out yet so they may originate here.
		if (null == _data)
		{
			_data = new HashMap<>();
			// We want to sleeze in our reduced size default testing prefs, here.
			_saveDefaultTestingPrefs();
		}
		byte[] bytes = _data.get(fileName);
		return (null != bytes)
				? new ByteArrayInputStream(bytes)
				: null
		;
	}

	@Override
	public OutputStream writeConfigFile(String fileName)
	{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		return new OutputStream()
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
	}

	@Override
	public String getDirectoryForReporting()
	{
		return "SYNTHETIC";
	}


	private void _saveDefaultTestingPrefs()
	{
		// For our tests, we specify a smaller maximum cache size (100 bytes) so that we can test it being constrained.
		GlobalPrefs prefs = new GlobalPrefs(GlobalPrefs.defaultPrefs().videoEdgePixelMax(), 100L);
		try (ObjectOutputStream stream = new ObjectOutputStream(this.writeConfigFile("global_prefs.dat")))
		{
			stream.writeObject(prefs);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
	}
}
