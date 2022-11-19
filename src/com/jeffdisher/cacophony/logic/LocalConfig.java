package com.jeffdisher.cacophony.logic;

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


/**
 * This is a basic wrapper over a loaded channel configuration.
 * NOTE:  It is likely going to be removed in the future, folded into the access design.
 */
public class LocalConfig
{
	/**
	 * Creates a new index, setting it as the shared instance.
	 * Note that this will write-back the initial channel state to storage.
	 * 
	 * @param ipfsConnectionString The IPFS connection string we will use for our connections.
	 * @param keyName The name of the IPFS key to use when publishing root elements.
	 * @return The config object for this new channel.
	 * @throws UsageException If there is already a loaded shared index or already one on disk.
	 * @throws IpfsConnectionException If there is an error connecting to the IPFS daemon.
	 */
	public static LocalConfig createNewConfig(IConfigFileSystem fileSystem, LocalDataModel dataModel, IConnectionFactory factory, String ipfsConnectionString, String keyName) throws UsageException, IpfsConnectionException
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
		try (IReadWriteLocalData writing = dataModel.openForWrite())
		{
			writing.writeLocalIndex(new LocalIndex(ipfsConnectionString, keyName, null));
			writing.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
			writing.writeGlobalPinCache(GlobalPinCache.newCache());
			writing.writeFollowIndex(FollowIndex.emptyFollowIndex());
		}
		catch (VersionException e)
		{
			// This won't happen when we are created a new data model.
			throw Assert.unexpected(e);
		}
		return new LocalConfig(fileSystem.getDirectoryForReporting(), factory, ipfsConnectionString, dataModel);
	}

	/**
	 * @return The config object of an existing channel.
	 * @throws UsageException If there is no existing shared index on disk.
	 * @throws VersionException The version file is missing or an unknown version.
	 */
	public static LocalConfig loadExistingConfig(IConfigFileSystem fileSystem, LocalDataModel dataModel, IConnectionFactory factory) throws UsageException, VersionException
	{
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (!doesExist)
		{
			throw new UsageException("Config doesn't exist");
		}
		String ipfsConnectionString = null;
		try (IReadOnlyLocalData reading = dataModel.openForRead())
		{
			ipfsConnectionString = reading.readLocalIndex().ipfsHost();
		}
		return new LocalConfig(fileSystem.getDirectoryForReporting(), factory, ipfsConnectionString, dataModel);
	}


	private final String _configDirectoryFullPath;
	private final LocalDataModel _localData;
	private final IConnectionFactory _factory;
	private final String _ipfsConnectionString;
	private IConnection _lazyConnection;

	private LocalConfig(String configDirectoryFullPath, IConnectionFactory factory, String ipfsConnectionString, LocalDataModel localData)
	{
		Assert.assertTrue(null != configDirectoryFullPath);
		Assert.assertTrue(null != factory);
		Assert.assertTrue(null != ipfsConnectionString);
		Assert.assertTrue(null != localData);
		
		_configDirectoryFullPath = configDirectoryFullPath;
		_localData = localData;
		_factory = factory;
		_ipfsConnectionString = ipfsConnectionString;
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
		return _configDirectoryFullPath;
	}

	private void _verifySharedConnections() throws IpfsConnectionException
	{
		if (null == _lazyConnection)
		{
			_lazyConnection = _factory.buildConnection(_ipfsConnectionString);
		}
	}
}
