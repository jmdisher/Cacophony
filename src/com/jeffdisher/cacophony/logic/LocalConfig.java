package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
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
	 * @throws IpfsConnectionException If there is an error connecting to the IPFS daemon.
	 */
	public static LocalConfig createNewConfig(IConfigFileSystem fileSystem, IConnectionFactory factory, String ipfsConnectionString, String keyName) throws UsageException, IpfsConnectionException
	{
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (doesExist)
		{
			throw new UsageException("Config already exists");
		}
		// We want to check that the connection works before we create the config file (otherwise we might store a broken config).
		IConnection connection = factory.buildConnection(ipfsConnectionString);
		Assert.assertTrue(null != connection);
		
		boolean didCreate = fileSystem.createConfigDirectory();
		if (!didCreate)
		{
			throw new UsageException("Failed to create config directory");
		}
		// Create the instance and populate it with default files.
		LocalDataModel dataModel;
		try
		{
			dataModel = new LocalDataModel(fileSystem);
		}
		catch (VersionException e)
		{
			// This won't happen when we are created a new data model.
			throw Assert.unexpected(e);
		}
		try (IReadWriteLocalData writing = dataModel.openForWrite())
		{
			writing.writeLocalIndex(new LocalIndex(ipfsConnectionString, keyName, null));
			writing.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
			writing.writeGlobalPinCache(GlobalPinCache.newCache());
			writing.writeFollowIndex(FollowIndex.emptyFollowIndex());
		}
		return new LocalConfig(fileSystem, factory, ipfsConnectionString, dataModel);
	}

	/**
	 * @return The shared LocalIndex instance, lazily loading it if needed.
	 * @throws UsageException If there is no existing shared index on disk.
	 * @throws VersionException The version file is missing or an unknown version.
	 */
	public static LocalConfig loadExistingConfig(IConfigFileSystem fileSystem, IConnectionFactory factory) throws UsageException, VersionException
	{
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (!doesExist)
		{
			throw new UsageException("Config doesn't exist");
		}
		LocalDataModel dataModel = new LocalDataModel(fileSystem);
		String ipfsConnectionString = null;
		try (IReadOnlyLocalData reading = dataModel.openForRead())
		{
			ipfsConnectionString = reading.readLocalIndex().ipfsHost();
		}
		return new LocalConfig(fileSystem, factory, ipfsConnectionString, dataModel);
	}


	private final IConfigFileSystem _fileSystem;
	private final LocalDataModel _localData;
	private final IConnectionFactory _factory;
	private final String _ipfsConnectionString;
	private IConnection _lazyConnection;

	private LocalConfig(IConfigFileSystem fileSystem, IConnectionFactory factory, String ipfsConnectionString, LocalDataModel localData)
	{
		Assert.assertTrue(null != fileSystem);
		Assert.assertTrue(null != factory);
		Assert.assertTrue(null != ipfsConnectionString);
		Assert.assertTrue(null != localData);
		
		_fileSystem = fileSystem;
		_localData = localData;
		_factory = factory;
		_ipfsConnectionString = ipfsConnectionString;
	}

	/**
	 * Builds a new DraftManager instance on top of the config's filesystem's draft directory.
	 * 
	 * @return The new DraftManager instance.
	 */
	public DraftManager buildDraftManager()
	{
		// We just use this helper to create the DraftManager so that we can continue to encapsulate the _fileSystem.
		try
		{
			return new DraftManager(_fileSystem.getDraftsTopLevelDirectory());
		}
		catch (IOException e)
		{
			// We don't currently know how/if we should best handle this error.
			throw Assert.unexpected(e);
		}
	}

	public LocalDataModel getSharedLocalData()
	{
		return _localData;
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

	private void _verifySharedConnections() throws IpfsConnectionException
	{
		if (null == _lazyConnection)
		{
			_lazyConnection = _factory.buildConnection(_ipfsConnectionString);
		}
	}
}
