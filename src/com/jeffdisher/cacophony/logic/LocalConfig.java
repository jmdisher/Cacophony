package com.jeffdisher.cacophony.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class LocalConfig implements ILocalConfig
{
	private static final String INDEX_FILE = "index.dat";
	private static final String GLOBAL_PREFS_FILE = "global_prefs.dat";
	private static final String GLOBAL_PIN_CACHE_FILE = "global_pin_cache.dat";
	private static final String FOLLOWING_INDEX_FILE = "following_index.dat";

	private final File _directory;
	private final IConnectionFactory _factory;
	private GlobalPinCache _lazyCache;
	private IConnection _lazyConnection;
	private FollowIndex _lazyFollowIndex;
	private LocalIndex _sharedLocalIndex;
	private GlobalPrefs _lazySharedPrefs;

	public LocalConfig(File directory, IConnectionFactory factory)
	{
		_directory = directory;
		_factory = factory;
	}

	@Override
	public LocalIndex createEmptyIndex(String ipfsConnectionString, String keyName) throws UsageException
	{
		File indexFile = new File(_directory, INDEX_FILE);
		if ((null != _sharedLocalIndex) || indexFile.exists())
		{
			throw new UsageException("Index already exists");
		}
		_sharedLocalIndex = new LocalIndex(ipfsConnectionString, keyName, null);
		return _sharedLocalIndex;
	}

	@Override
	public LocalIndex readExistingSharedIndex() throws UsageException
	{
		if (null == _sharedLocalIndex)
		{
			_sharedLocalIndex = _readFile(INDEX_FILE, LocalIndex.class);
			if (null == _sharedLocalIndex)
			{
				throw new UsageException("Index file not found");
			}
		}
		return _sharedLocalIndex;
	}

	@Override
	public void storeSharedIndex(LocalIndex localIndex)
	{
		_sharedLocalIndex = localIndex;
		_storeFile(INDEX_FILE, _sharedLocalIndex);
	}

	@Override
	public GlobalPrefs readSharedPrefs()
	{
		if (null == _lazySharedPrefs)
		{
			GlobalPrefs prefs = _readFile(GLOBAL_PREFS_FILE, GlobalPrefs.class);
			if (null == prefs)
			{
				// We want to default the prefs if there isn't one.
				prefs = GlobalPrefs.defaultPrefs();
			}
			_lazySharedPrefs = prefs;
		}
		return _lazySharedPrefs;
	}

	@Override
	public void storeSharedPrefs(GlobalPrefs prefs)
	{
		_lazySharedPrefs = prefs;
		_storeFile(GLOBAL_PREFS_FILE, _lazySharedPrefs);
	}

	@Override
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

	@Override
	public FollowIndex loadFollowIndex()
	{
		if (null == _lazyFollowIndex)
		{
			File file = new File(_directory, FOLLOWING_INDEX_FILE);
			if (file.exists())
			{
				try (FileInputStream stream = new FileInputStream(file))
				{
					_lazyFollowIndex = FollowIndex.fromStream(stream);
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
				_lazyFollowIndex = FollowIndex.emptyFollowIndex();
			}
		}
		return _lazyFollowIndex;
	}

	public void storeFollowIndex()
	{
		if (null != _lazyFollowIndex)
		{
			File indexFile = new File(_directory, FOLLOWING_INDEX_FILE);
			try (FileOutputStream stream = new FileOutputStream(indexFile))
			{
				_lazyFollowIndex.writeToStream(stream);
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

	@Override
	public IConnection getSharedConnection() throws IpfsConnectionException
	{
		_verifySharedConnections();
		return _lazyConnection;
	}

	@Override
	public String getConfigDirectoryFullPath()
	{
		return _directory.getAbsolutePath();
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

	private void _verifySharedConnections() throws IpfsConnectionException
	{
		if (null == _lazyConnection)
		{
			// We should not be trying to open a connection if there is no existing index.
			Assert.assertTrue(null != _sharedLocalIndex);
			_lazyConnection = _factory.buildConnection(_sharedLocalIndex.ipfsHost());
		}
	}
}
