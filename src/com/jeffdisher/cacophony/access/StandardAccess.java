package com.jeffdisher.cacophony.access;

import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The standard factories and implementation of the access design.
 * The general idea is that higher-level components of the system should not be concerned with the individual parts of
 * the underlying implementation.
 * A thread needs to request the access type it wants, closing it when finished.  Those access types have different
 * interfaces which implement the underlying functional primitives of the system.
 * Note that the point of this design is that it doesn't distinguish between local storage versus network access
 * primitives, allowing the accessing code to interact in as high-level a way as possible without needing to manage
 * those individual parts of the system which would otherwise expose more specific pieces of primitive functionality.
 * 
 * NOTE:  Some of the methods currently exposed are just temporary, while the system transitions to this new design.
 * Eventually, these methods which grant direct access to the underlying components will be removed as they are replaced
 * with abstract high-level calls.
 */
public class StandardAccess implements IWritingAccess
{
	/**
	 * Requests read access.
	 * 
	 * @param environment The environment.
	 * @return The read access interface.
	 * @throws UsageException If the config directory is missing.
	 * @throws VersionException The version file is missing or an unknown version.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 */
	public static IReadingAccess readAccess(IEnvironment environment) throws UsageException, VersionException, IpfsConnectionException
	{
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (!doesExist)
		{
			throw new UsageException("Config doesn't exist");
		}
		LocalDataModel dataModel = environment.getSharedDataModel();
		IReadOnlyLocalData reading = dataModel.openForRead();
		
		String ipfsConnectionString = reading.readLocalIndex().ipfsHost();
		IConnection connection = environment.getConnectionFactory().buildConnection(ipfsConnectionString);
		Assert.assertTrue(null != connection);
		
		return new StandardAccess(environment, connection, reading, null);
	}

	/**
	 * Requests write access.
	 * 
	 * @param environment The environment.
	 * @return The write access interface.
	 * @throws UsageException If the config directory is missing.
	 * @throws VersionException The version file is missing or an unknown version.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 */
	public static IWritingAccess writeAccess(IEnvironment environment) throws UsageException, VersionException, IpfsConnectionException
	{
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (!doesExist)
		{
			throw new UsageException("Config doesn't exist");
		}
		LocalDataModel dataModel = environment.getSharedDataModel();
		IReadWriteLocalData writing = dataModel.openForWrite();
		
		String ipfsConnectionString = writing.readLocalIndex().ipfsHost();
		IConnection connection = environment.getConnectionFactory().buildConnection(ipfsConnectionString);
		Assert.assertTrue(null != connection);
		
		return new StandardAccess(environment, connection, writing, writing);
	}

	/**
	 * Creates a new channel storage and opens it for write access.
	 * 
	 * @param environment The environment.
	 * @param ipfsConnectionString The IPFS connection string from daemon startup (the "/ip4/127.0.0.1/tcp/5001" from
	 * "API server listening on /ip4/127.0.0.1/tcp/5001").
	 * @param keyName The name of the key to use for publishing.
	 * @return The write access interface.
	 * @throws UsageException If the config directory already exists or couldn't be created.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 * @throws VersionException If the version file was an unknown number or was missing when data exists.
	 */
	public static IWritingAccess createForWrite(IEnvironment environment, String ipfsConnectionString, String keyName) throws UsageException, IpfsConnectionException, VersionException
	{
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		// First, make sure it doesn't already exist.
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (doesExist)
		{
			throw new UsageException("Config already exists");
		}
		// We want to check that the connection works before we create the config file (otherwise we might store a broken config).
		IConnection connection = environment.getConnectionFactory().buildConnection(ipfsConnectionString);
		Assert.assertTrue(null != connection);
		
		boolean didCreate = fileSystem.createConfigDirectory();
		if (!didCreate)
		{
			throw new UsageException("Failed to create config directory");
		}
		// Create the instance and populate it with default files.
		LocalDataModel dataModel = environment.getSharedDataModel();
		IReadWriteLocalData writing = dataModel.openForWrite();
		try
		{
			writing.writeLocalIndex(new LocalIndex(ipfsConnectionString, keyName, null));
			writing.writeGlobalPrefs(GlobalPrefs.defaultPrefs());
			writing.writeGlobalPinCache(GlobalPinCache.newCache());
			writing.writeFollowIndex(FollowIndex.emptyFollowIndex());
		}
		catch (Throwable t)
		{
			writing.close();
			throw t;
		}
		
		// Now, pass this open read-write abstraction into the new StandardAccess instance.
		return new StandardAccess(environment, connection, writing, writing);
	}


	private final IEnvironment _environment;
	private final IConnection _sharedConnection;
	private final IReadOnlyLocalData _readOnly;
	private final IReadWriteLocalData _readWrite;
	
	private GlobalPinCache _pinCache;
	private boolean _writePinCache;
	private FollowIndex _followIndex;
	private boolean _writeFollowIndex;

	private StandardAccess(IEnvironment environment, IConnection sharedConnection, IReadOnlyLocalData readOnly, IReadWriteLocalData readWrite)
	{
		_environment = environment;
		_sharedConnection = sharedConnection;
		_readOnly = readOnly;
		_readWrite = readWrite;
	}

	@Override
	public LocalIndex readOnlyLocalIndex()
	{
		return _readOnly.readLocalIndex();
	}

	@Override
	public INetworkScheduler scheduler() throws IpfsConnectionException
	{
		LocalIndex localIndex = _readOnly.readLocalIndex();
		return _environment.getSharedScheduler(_sharedConnection, localIndex.keyName());
	}

	@Override
	public HighLevelCache loadCacheReadOnly() throws IpfsConnectionException
	{
		LocalIndex localIndex = _readOnly.readLocalIndex();
		INetworkScheduler scheduler = _environment.getSharedScheduler(_sharedConnection, localIndex.keyName());
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
		}
		return new HighLevelCache(_pinCache, scheduler, _sharedConnection);
	}

	@Override
	public FollowIndex readOnlyFollowIndex()
	{
		if (null == _followIndex)
		{
			_followIndex = _readOnly.readFollowIndex();
		}
		return _followIndex;
	}

	@Override
	public IConnection connection() throws IpfsConnectionException
	{
		return _sharedConnection;
	}

	@Override
	public LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator)
	{
		return _readOnly.lazilyLoadFolloweeCache(cacheGenerator);
	}

	@Override
	public boolean isInPinCached(IpfsFile file)
	{
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
		}
		return _pinCache.isCached(file);
	}

	@Override
	public GlobalPrefs readGlobalPrefs()
	{
		return _readOnly.readGlobalPrefs();
	}

	@Override
	public void requestIpfsGc() throws IpfsConnectionException
	{
		_sharedConnection.requestStorageGc();
	}

	@Override
	public void updateIndexHash(IpfsFile newIndexHash)
	{
		Assert.assertTrue(null != _readWrite);
		LocalIndex oldLocalIndex = _readOnly.readLocalIndex();
		_readWrite.writeLocalIndex(new LocalIndex(oldLocalIndex.ipfsHost(), oldLocalIndex.keyName(), newIndexHash));
	}

	@Override
	public HighLevelCache loadCacheReadWrite() throws IpfsConnectionException
	{
		Assert.assertTrue(null != _readWrite);
		LocalIndex localIndex = _readOnly.readLocalIndex();
		INetworkScheduler scheduler = _environment.getSharedScheduler(_sharedConnection, localIndex.keyName());
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
		}
		// We will want to write this back.
		_writePinCache = true;
		return new HighLevelCache(_pinCache, scheduler, _sharedConnection);
	}

	@Override
	public FollowIndex readWriteFollowIndex()
	{
		Assert.assertTrue(null != _readWrite);
		if (null == _followIndex)
		{
			_followIndex = _readWrite.readFollowIndex();
		}
		// We will want to write this back.
		_writeFollowIndex = true;
		return _followIndex;
	}

	@Override
	public void writeGlobalPrefs(GlobalPrefs prefs)
	{
		Assert.assertTrue(null != _readWrite);
		_readWrite.writeGlobalPrefs(prefs);
	}

	@Override
	public void close()
	{
		if (_writePinCache)
		{
			_readWrite.writeGlobalPinCache(_pinCache);
		}
		if (_writeFollowIndex)
		{
			_readWrite.writeFollowIndex(_followIndex);
		}
		// The read/write references are the same, when both present, but read-only is always present so close it.
		_readOnly.close();
	}
}
