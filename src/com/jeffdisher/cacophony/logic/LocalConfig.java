package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class LocalConfig
{
	/**
	 * Creates a new index, setting it as the shared instance.  Does NOT write to disk.
	 * 
	 * @param ipfsConnectionString The IPFS connection string we will use for our connections.
	 * @param keyName The name of the IPFS key to use when publishing root elements.
	 * @return The shared index.
	 * @throws UsageException If there is already a loaded shared index or already one on disk.
	 */
	public static LocalConfig createNewConfig(IConfigFileSystem fileSystem, IConnectionFactory factory, String ipfsConnectionString, String keyName) throws UsageException
	{
		boolean didCreate = fileSystem.createConfigDirectory();
		if (!didCreate)
		{
			throw new UsageException("Config already exists");
		}
		// Create the instance and populate it with default files.
		LocalConfig config = new LocalConfig(fileSystem, factory);
		config.storeSharedIndex(new LocalIndex(ipfsConnectionString, keyName, null));
		config.storeSharedPrefs(GlobalPrefs.defaultPrefs());
		config._lazyCache = GlobalPinCache.newCache();
		config._lazyFollowIndex = FollowIndex.emptyFollowIndex();
		config.writeBackConfig();
		return config;
	}

	/**
	 * @return The shared LocalIndex instance, lazily loading it if needed.
	 * @throws UsageException If there is no existing shared index on disk.
	 */
	public static LocalConfig loadExistingConfig(IConfigFileSystem fileSystem, IConnectionFactory factory) throws UsageException
	{
		boolean didCreate = fileSystem.createConfigDirectory();
		if (didCreate)
		{
			throw new UsageException("Config doesn't exist");
		}
		return new LocalConfig(fileSystem, factory);
	}


	private static final String INDEX_FILE = "index1.dat";
	private static final String GLOBAL_PREFS_FILE = "global_prefs1.dat";
	private static final String GLOBAL_PIN_CACHE_FILE = "global_pin_cache1.dat";
	private static final String FOLLOWING_INDEX_FILE = "following_index1.dat";

	private final IConfigFileSystem _fileSystem;
	private final IConnectionFactory _factory;
	private LocalIndex _sharedLocalIndex;

	private GlobalPinCache _lazyCache;
	private IConnection _lazyConnection;
	private FollowIndex _lazyFollowIndex;
	private GlobalPrefs _lazySharedPrefs;

	private LocalConfig(IConfigFileSystem fileSystem, IConnectionFactory factory)
	{
		_fileSystem = fileSystem;
		_factory = factory;
	}

	public LocalIndex readLocalIndex()
	{
		if (null == _sharedLocalIndex)
		{
			_sharedLocalIndex = _readFile(INDEX_FILE, LocalIndex.class);
			// This can never be null.
			Assert.assertTrue(null != _sharedLocalIndex);
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
			_lazySharedPrefs = _readFile(GLOBAL_PREFS_FILE, GlobalPrefs.class);
			// This MUST have been created during default creation.
			Assert.assertTrue(null != _lazySharedPrefs);
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
			// This MUST have been created during default creation.
			Assert.assertTrue(null != stream);
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
		return _lazyCache;
	}

	public FollowIndex loadFollowIndex()
	{
		if (null == _lazyFollowIndex)
		{
			InputStream stream = _fileSystem.readConfigFile(FOLLOWING_INDEX_FILE);
			// This MUST have been created during default creation.
			Assert.assertTrue(null != stream);
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
		return _lazyFollowIndex;
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

	public void writeBackConfig()
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
