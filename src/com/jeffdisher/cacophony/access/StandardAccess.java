package com.jeffdisher.cacophony.access;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
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
	 */
	public static IReadingAccess readAccess(IEnvironment environment) throws UsageException, VersionException
	{
		LocalConfig local = environment.loadExistingConfig();
		IReadOnlyLocalData data = local.getSharedLocalData().openForRead();
		try
		{
			return new StandardAccess(environment, local, data, null);
		}
		catch (Throwable t)
		{
			data.close();
			throw t;
		}
	}

	/**
	 * Requests write access.
	 * 
	 * @param environment The environment.
	 * @return The write access interface.
	 * @throws UsageException If the config directory is missing.
	 * @throws VersionException The version file is missing or an unknown version.
	 */
	public static IWritingAccess writeAccess(IEnvironment environment) throws UsageException, VersionException
	{
		LocalConfig local = environment.loadExistingConfig();
		IReadWriteLocalData data = local.getSharedLocalData().openForWrite();
		try
		{
			return new StandardAccess(environment, local, data, data);
		}
		catch (Throwable t)
		{
			data.close();
			throw t;
		}
	}


	private final IEnvironment _environment;
	private final LocalConfig _local;
	private final IReadOnlyLocalData _readOnly;
	private final IReadWriteLocalData _readWrite;
	
	private GlobalPinCache _pinCache;
	private boolean _writePinCache;
	private FollowIndex _followIndex;
	private boolean _writeFollowIndex;

	private StandardAccess(IEnvironment environment, LocalConfig local, IReadOnlyLocalData readOnly, IReadWriteLocalData readWrite)
	{
		_environment = environment;
		_local = local;
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
		IConnection connection = _local.getSharedConnection();
		return _environment.getSharedScheduler(connection, localIndex.keyName());
	}

	@Override
	public HighLevelCache loadCacheReadOnly() throws IpfsConnectionException
	{
		LocalIndex localIndex = _readOnly.readLocalIndex();
		IConnection connection = _local.getSharedConnection();
		INetworkScheduler scheduler = _environment.getSharedScheduler(connection, localIndex.keyName());
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
		}
		return new HighLevelCache(_pinCache, scheduler, connection);
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
	public GlobalPrefs readGlobalPrefs()
	{
		return _readOnly.readGlobalPrefs();
	}

	@Override
	public void requestIpfsGc() throws IpfsConnectionException
	{
		_local.getSharedConnection().requestStorageGc();
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
		IConnection connection = _local.getSharedConnection();
		INetworkScheduler scheduler = _environment.getSharedScheduler(connection, localIndex.keyName());
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
		}
		// We will want to write this back.
		_writePinCache = true;
		return new HighLevelCache(_pinCache, scheduler, connection);
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
