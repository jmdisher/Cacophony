package com.jeffdisher.cacophony.access;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
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
	
	private INetworkScheduler _scheduler;
	private GlobalPinCache _pinCache;
	private boolean _writePinCache;
	private FolloweeData _followeeData;
	private boolean _writeFollowIndex;

	private StandardAccess(IEnvironment environment, IConnection sharedConnection, IReadOnlyLocalData readOnly, IReadWriteLocalData readWrite)
	{
		_environment = environment;
		_sharedConnection = sharedConnection;
		_readOnly = readOnly;
		_readWrite = readWrite;
	}

	@Override
	public IFolloweeReading readableFolloweeData()
	{
		if (null == _followeeData)
		{
			_followeeData = FolloweeData.buildOnIndex(_readOnly.readFollowIndex());
		}
		return _followeeData;
	}

	@Override
	public IConnection connection()
	{
		return _sharedConnection;
	}

	@Override
	public LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator)
	{
		// NOTE:  The underlying data store actually manages the locking around this generation so we can just defer to it.
		return _readOnly.lazilyLoadFolloweeCache(cacheGenerator);
	}

	@Override
	public boolean isInPinCached(IpfsFile file)
	{
		_lazyLoadPinCache();
		return _pinCache.isCached(file);
	}

	@Override
	public PrefsData readPrefs()
	{
		return PrefsData.buildOnPrefs(_readOnly.readGlobalPrefs());
	}

	@Override
	public void requestIpfsGc() throws IpfsConnectionException
	{
		_sharedConnection.requestStorageGc();
	}

	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(null != file);
		_lazyLoadPinCache();
		_lazyCreateScheduler();
		Assert.assertTrue(_pinCache.isCached(file));
		return _scheduler.readData(file, decoder);
	}

	@Override
	public <R> FutureRead<R> loadNotCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(null != file);
		_lazyLoadPinCache();
		_lazyCreateScheduler();
		if (_pinCache.isCached(file))
		{
			_environment.logError("WARNING!  Not expected in cache:  " + file);
		}
		return _scheduler.readData(file, decoder);
	}

	@Override
	public URL getCachedUrl(IpfsFile file)
	{
		Assert.assertTrue(null != file);
		_lazyLoadPinCache();
		Assert.assertTrue(_pinCache.isCached(file));
		return _sharedConnection.urlForDirectFetch(file);
	}

	@Override
	public IpfsFile getLastRootElement()
	{
		return _readOnly.readLocalIndex().lastPublishedIndex();
	}

	@Override
	public IpfsKey getPublicKey()
	{
		_lazyCreateScheduler();
		return _scheduler.getPublicKey();
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		_lazyCreateScheduler();
		return _scheduler.resolvePublicKey(keyToResolve);
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		_lazyCreateScheduler();
		return _scheduler.getSizeInBytes(cid);
	}

	@Override
	public FuturePublish republishIndex()
	{
		_lazyCreateScheduler();
		IpfsFile lastRoot = _readOnly.readLocalIndex().lastPublishedIndex();
		return _scheduler.publishIndex(lastRoot);
	}

	// ----- Writing methods -----
	@Override
	public IFolloweeWriting writableFolloweeData()
	{
		Assert.assertTrue(null != _readWrite);
		if (null == _followeeData)
		{
			_followeeData = FolloweeData.buildOnIndex(_readWrite.readFollowIndex());
		}
		// We will want to write this back.
		_writeFollowIndex = true;
		return _followeeData;
	}

	@Override
	public void writePrefs(PrefsData prefs)
	{
		Assert.assertTrue(null != _readWrite);
		_readWrite.writeGlobalPrefs(prefs.serializeToPrefs());
	}

	@Override
	public IpfsFile uploadAndPin(InputStream dataToSave, boolean shouldCloseStream) throws IpfsConnectionException
	{
		_lazyCreateScheduler();
		FutureSave save = _scheduler.saveStream(dataToSave, shouldCloseStream);
		IpfsFile hash = save.get();
		_lazyLoadPinCache();
		_pinCache.hashWasAdded(hash);
		return hash;
	}

	@Override
	public FuturePublish uploadStoreAndPublishIndex(StreamIndex streamIndex) throws IpfsConnectionException
	{
		Assert.assertTrue(null != _readWrite);
		_lazyCreateScheduler();
		FutureSave save = _scheduler.saveStream(new ByteArrayInputStream(GlobalData.serializeIndex(streamIndex)), true);
		IpfsFile hash = save.get();
		_lazyLoadPinCache();
		_pinCache.hashWasAdded(hash);
		LocalIndex oldLocalIndex = _readOnly.readLocalIndex();
		_readWrite.writeLocalIndex(new LocalIndex(oldLocalIndex.ipfsHost(), oldLocalIndex.keyName(), hash));
		return _scheduler.publishIndex(hash);
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		_lazyLoadPinCache();
		_lazyCreateScheduler();
		boolean shouldPin = _pinCache.shouldPinAfterAdding(cid);
		FuturePin pin = null;
		if (shouldPin)
		{
			pin = _scheduler.pin(cid);
		}
		else
		{
			// If we decided that this was already pinned, just return an already completed pin.
			pin = new FuturePin();
			pin.success();
		}
		return pin;
	}

	@Override
	public void unpin(IpfsFile cid) throws IpfsConnectionException
	{
		_lazyLoadPinCache();
		_lazyCreateScheduler();
		boolean shouldUnpin = _pinCache.shouldUnpinAfterRemoving(cid);
		if (shouldUnpin)
		{
			_scheduler.unpin(cid).get();
		}
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
			_readWrite.writeFollowIndex(_followeeData.serializeToIndex());
		}
		// The read/write references are the same, when both present, but read-only is always present so close it.
		_readOnly.close();
	}


	private void _lazyLoadPinCache()
	{
		if (null == _pinCache)
		{
			_pinCache = _readOnly.readGlobalPinCache();
			// If we are in writable mode, assume we need to write this back.
			_writePinCache = (null != _readWrite);
		}
	}

	/**
	 * Note that the scheduler MUST be created lazily since this avoids a bootstrapping problem:  The scheduler needs
	 * to be able to look up the publishing key (since we want it to have everything it needs before being started) but
	 * the key is created _after_ the StandardAccess is first created, when creating a channel (since it relies on the
	 * bare connection and the storage).
	 * @throws IpfsConnectionException 
	 */
	private void _lazyCreateScheduler()
	{
		if (null == _scheduler)
		{
			LocalIndex localIndex = _readOnly.readLocalIndex();
			try
			{
				_scheduler = _environment.getSharedScheduler(_sharedConnection, localIndex.keyName());
			}
			catch (IpfsConnectionException e)
			{
				// This sudden failure to contact the node isn't something we can meaningfully handle at this point in the run and is incredibly unlikely.
				// TODO:  See if we can fix the start-up ordering in order to move this failure case somewhere earlier.
				throw Assert.unexpected(e);
			}
		}
	}
}
