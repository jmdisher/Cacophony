package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;


public class LocalActions
{
	private static final String INDEX_FILE = "index.dat";
	private static final String GLOBAL_PREFS_FILE = "global_prefs.dat";
	private static final String GLOBAL_PIN_CACHE_FILE = "global_pin_cache.dat";

	private final File _directory;
	private GlobalPinCache _lazyCache;
	private IPFS _lazyConnection;

	public LocalActions(File directory)
	{
		_directory = directory;
	}

	public LocalIndex readIndex()
	{
		return _readFile(INDEX_FILE, LocalIndex.class);
	}

	public void storeIndex(LocalIndex index)
	{
		_storeFile(INDEX_FILE, index);
	}

	public GlobalPrefs readPrefs()
	{
		GlobalPrefs prefs = _readFile(GLOBAL_PREFS_FILE, GlobalPrefs.class);
		if (null == prefs)
		{
			// We want to default the prefs if there isn't one.
			int width = 1024;
			int height = 768;
			long cacheMaxBytes = 1_000_000_000L;
			prefs = new GlobalPrefs(width, height, cacheMaxBytes);
		}
		return prefs;
	}

	public void storePrefs(GlobalPrefs prefs)
	{
		_storeFile(GLOBAL_PREFS_FILE, prefs);
	}

	public GlobalPinCache loadGlobalPinCache()
	{
		if (null == _lazyCache)
		{
			File file = new File(_directory, GLOBAL_PIN_CACHE_FILE);
			if (file.exists())
			{
				try (FileInputStream stream = new FileInputStream(file))
				{
					_lazyCache = GlobalPinCache.fromStream(stream);
				}
				catch (FileNotFoundException e)
				{
					// We just checked this so it can't happen.
					throw Assert.unexpected(e);
				}
				catch (IOException e)
				{
					// We don't expect this.
					throw Assert.unexpected(e);
				}
			}
			else
			{
				_lazyCache = GlobalPinCache.newCache();
			}
		}
		return _lazyCache;
	}

	public void storeGlobalPinCache()
	{
		if (null != _lazyCache)
		{
			File indexFile = new File(_directory, GLOBAL_PIN_CACHE_FILE);
			try (FileOutputStream stream = new FileOutputStream(indexFile))
			{
				_lazyCache.writeToStream(stream);
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

	public IPFS getSharedConnection()
	{
		if (null == _lazyConnection)
		{
			// We don't reuse the LocalIndex since other readers of this likely want to change it and we only want to read it.
			LocalIndex index = _readFile(INDEX_FILE, LocalIndex.class);
			// We should not be trying to open a connection if there is no existing index.
			Assert.assertTrue(null != index);
			_lazyConnection = new IPFS(index.ipfsHost());
		}
		return _lazyConnection;
	}


	private <T> T _readFile(String fileName, Class<T> clazz)
	{
		File indexFile = new File(_directory, fileName);
		T object = null;
		try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(indexFile)))
		{
			try
			{
				object = clazz.cast(stream.readObject());
			}
			catch (ClassNotFoundException e)
			{
				throw Assert.unexpected(e);
			}
		}
		catch (FileNotFoundException e)
		{
			// This is acceptable and we just return null - means there is no data.
			object = null;
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		return object;
	}

	private <T> void _storeFile(String fileName, T object)
	{
		File indexFile = new File(_directory, fileName);
		try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(indexFile)))
		{
			stream.writeObject(object);
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
