package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class LocalConfig
{
	private static final String INDEX_FILE = "index.dat";
	private static final String GLOBAL_PREFS_FILE = "global_prefs.dat";
	private static final String GLOBAL_PIN_CACHE_FILE = "global_pin_cache.dat";
	private static final String FOLLOWING_INDEX_FILE = "following_index.dat";

	private final IConfigFileSystem _fileSystem;
	private final IConnectionFactory _factory;
	private GlobalPinCache _lazyCache;
	private IConnection _lazyConnection;
	private FollowIndex _lazyFollowIndex;
	private LocalIndex _sharedLocalIndex;
	private GlobalPrefs _lazySharedPrefs;

	public LocalConfig(IConfigFileSystem fileSystem, IConnectionFactory factory)
	{
		_fileSystem = fileSystem;
		_factory = factory;
	}

	/**
	 * Creates a new index, setting it as the shared instance.  Does NOT write to disk.
	 * 
	 * @param ipfsConnectionString The IPFS connection string we will use for our connections.
	 * @param keyName The name of the IPFS key to use when publishing root elements.
	 * @return The shared index.
	 * @throws UsageException If there is already a loaded shared index or already one on disk.
	 */
	public LocalIndex createEmptyIndex(String ipfsConnectionString, String keyName) throws UsageException
	{
		boolean didCreate = _fileSystem.createConfigDirectory();
		if (!didCreate)
		{
			throw new UsageException("Index already exists");
		}
		_sharedLocalIndex = new LocalIndex(ipfsConnectionString, keyName, null);
		return _sharedLocalIndex;
	}

	/**
	 * @return The shared LocalIndex instance, lazily loading it if needed.
	 * @throws UsageException If there is no existing shared index on disk.
	 */
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

	/**
	 * Sets the given localIndex as the new shared index and writes it to disk.
	 * 
	 * @param localIndex The new local index.
	 */
	public void storeSharedIndex(LocalIndex localIndex)
	{
		_sharedLocalIndex = localIndex;
		_storeFile(INDEX_FILE, _sharedLocalIndex);
	}

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

	public void storeSharedPrefs(GlobalPrefs prefs)
	{
		_lazySharedPrefs = prefs;
		_storeFile(GLOBAL_PREFS_FILE, _lazySharedPrefs);
	}

	public GlobalPinCache loadGlobalPinCache()
	{
		if (null == _lazyCache)
		{
			InputStream stream = _fileSystem.readConfigFile(GLOBAL_PIN_CACHE_FILE);
			if (null != stream)
			{
				_lazyCache = GlobalPinCache.fromStream(stream);
				try
				{
					stream.close();
				}
				catch (IOException e)
				{
					// Failure on close not expected.
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
			try (OutputStream stream = _fileSystem.writeConfigFile(GLOBAL_PIN_CACHE_FILE))
			{
				_lazyCache.writeToStream(stream);
			}
			catch (IOException e)
			{
				// Failure not expected.
				throw Assert.unexpected(e);
			}
		}
	}

	public FollowIndex loadFollowIndex()
	{
		if (null == _lazyFollowIndex)
		{
			InputStream stream = _fileSystem.readConfigFile(FOLLOWING_INDEX_FILE);
			if (null != stream)
			{
				_lazyFollowIndex = FollowIndex.fromStream(stream);
				try
				{
					stream.close();
				}
				catch (IOException e)
				{
					// Failure on close not expected.
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
			try (OutputStream stream = _fileSystem.writeConfigFile(FOLLOWING_INDEX_FILE))
			{
				_lazyFollowIndex.writeToStream(stream);
			}
			catch (IOException e)
			{
				// Failure not expected.
				throw Assert.unexpected(e);
			}
		}
	}

	public IConnection getSharedConnection() throws IpfsConnectionException
	{
		_verifySharedConnections();
		return _lazyConnection;
	}

	/**
	 * This is purely to improve error reporting - returns the full path to the configuration directory.
	 * 
	 * @return The full path to the configuration directory.
	 */
	public String getConfigDirectoryFullPath()
	{
		return _fileSystem.getDirectoryForReporting();
	}


	private <T> T _readFile(String fileName, Class<T> clazz)
	{
		T object = null;
		InputStream rawStream = _fileSystem.readConfigFile(fileName);
		if (null != rawStream)
		{
			try (ObjectInputStream stream = new ObjectInputStream(rawStream))
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
			catch (IOException e)
			{
				// We don't expect this.
				throw Assert.unexpected(e);
			}
		}
		return object;
	}

	private <T> void _storeFile(String fileName, T object)
	{
		try (ObjectOutputStream stream = new ObjectOutputStream(_fileSystem.writeConfigFile(fileName)))
		{
			stream.writeObject(object);
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
