package com.jeffdisher.cacophony.access;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PinCacheData;
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
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
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
	 * Note that this assumes that the config directory has already been created and is valid (see
	 * "StandardAccess.createNewChannelConfig" and "LocalDataModel.verifyStorageConsistency").
	 * 
	 * @param environment The environment.
	 * @return The read access interface.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 */
	public static IReadingAccess readAccess(IEnvironment environment, ILogger logger) throws IpfsConnectionException
	{
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		// Should have been created earlier in the run.
		Assert.assertTrue(doesExist);
		LocalDataModel dataModel = environment.getSharedDataModel();
		IReadOnlyLocalData reading = dataModel.openForRead();
		
		return new StandardAccess(environment, logger, reading, null);
	}

	/**
	 * Requests write access.
	 * Note that this assumes that the config directory has already been created and is valid (see
	 * "StandardAccess.createNewChannelConfig" and "LocalDataModel.verifyStorageConsistency").
	 * 
	 * @param environment The environment.
	 * @return The write access interface.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 */
	public static IWritingAccess writeAccess(IEnvironment environment, ILogger logger) throws IpfsConnectionException
	{
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		// Should have been created earlier in the run.
		Assert.assertTrue(doesExist);
		LocalDataModel dataModel = environment.getSharedDataModel();
		IReadWriteLocalData writing = dataModel.openForWrite();
		
		return new StandardAccess(environment, logger, writing, writing);
	}

	/**
	 * Creates a new on-disk data location for our data.  This is expected to be tried on most launches, sometimes with
	 * incomplete data, so it will just return false and do nothing if there already seems to be data present.
	 * 
	 * @param environment The environment.
	 * @param ipfsConnectionString The IPFS connection string from daemon startup (the "/ip4/127.0.0.1/tcp/5001" from
	 * "API server listening on /ip4/127.0.0.1/tcp/5001").
	 * @param keyName The name of the key to use for publishing (may be null if this wasn't provided).
	 * @return True if the config was created with default data or false if it wasn't touched as it already existed.
	 * @throws UsageException If the config directory couldn't be created.
	 * @throws IpfsConnectionException If there was an issue contacting the IPFS server.
	 */
	public static boolean createNewChannelConfig(IEnvironment environment, String ipfsConnectionString, String keyName) throws UsageException, IpfsConnectionException
	{
		boolean didCreate = false;
		
		// Get the filesystem of our configured directory.
		IConfigFileSystem fileSystem = environment.getConfigFileSystem();
		
		// First, make sure it doesn't already exist.
		boolean doesExist = fileSystem.doesConfigDirectoryExist();
		if (!doesExist)
		{
			// We want to check that the connection works before we create the config file (otherwise we might store a broken config).
			IConnection connection = environment.getConnection();
			Assert.assertTrue(null != connection);
			
			didCreate = fileSystem.createConfigDirectory();
			if (!didCreate)
			{
				throw new UsageException("Failed to create config directory");
			}
			// Create the instance and populate it with default files.
			LocalDataModel dataModel = environment.getSharedDataModel();
			try (IReadWriteLocalData writing = dataModel.openForWrite())
			{
				writing.writeLocalIndex(ChannelData.create(ipfsConnectionString, keyName));
				writing.writeGlobalPrefs(PrefsData.defaultPrefs());
				writing.writeGlobalPinCache(PinCacheData.createEmpty());
				writing.writeFollowIndex(FolloweeData.createEmpty());
			}
		}
		return didCreate;
	}


	private final ILogger _logger;
	private final IConnection _sharedConnection;
	private final String _keyName;
	private final IpfsKey _publicKey;
	private final INetworkScheduler _scheduler;
	private final IReadOnlyLocalData _readOnly;
	private final IReadWriteLocalData _readWrite;
	
	private final PinCacheData _pinCache;
	private final FolloweeData _followeeData;
	private boolean _writeFollowIndex;
	private final ChannelData _channelData;
	private boolean _writeChannelData;

	private StandardAccess(IEnvironment environment, ILogger logger, IReadOnlyLocalData readOnly, IReadWriteLocalData readWrite) throws IpfsConnectionException
	{
		PinCacheData pinCache = readOnly.readGlobalPinCache();
		Assert.assertTrue(null != pinCache);
		FolloweeData followeeData = readOnly.readFollowIndex();
		Assert.assertTrue(null != followeeData);
		
		// Note that we only use the lastPublishedIndex from ChannelData.
		// TODO:  Remove this with the next storage version number change.
		ChannelData localIndex = readOnly.readLocalIndex();
		Assert.assertTrue(null != localIndex);
		IConnection connection = environment.getConnection();
		Assert.assertTrue(null != connection);
		String keyName = environment.getKeyName();
		IpfsKey publicKey = environment.getPublicKey();
		INetworkScheduler scheduler = environment.getSharedScheduler();
		Assert.assertTrue(null != scheduler);
		
		_logger = logger;
		_sharedConnection = connection;
		_keyName = keyName;
		_publicKey = publicKey;
		_scheduler = scheduler;
		_readOnly = readOnly;
		_readWrite = readWrite;
		
		_pinCache = pinCache;
		_followeeData = followeeData;
		_channelData = localIndex;
	}

	@Override
	public IFolloweeReading readableFolloweeData()
	{
		return _followeeData;
	}

	@Override
	public boolean isInPinCached(IpfsFile file)
	{
		return _pinCache.isPinned(file);
	}

	@Override
	public PrefsData readPrefs()
	{
		return _readOnly.readGlobalPrefs();
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
		Assert.assertTrue(_pinCache.isPinned(file));
		return _scheduler.readData(file, decoder);
	}

	@Override
	public <R> FutureRead<R> loadNotCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(null != file);
		if (_pinCache.isPinned(file))
		{
			_logger.logVerbose("WARNING!  Not expected in cache:  " + file);
		}
		return _scheduler.readData(file, decoder);
	}

	@Override
	public URL getCachedUrl(IpfsFile file)
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_pinCache.isPinned(file));
		return _sharedConnection.urlForDirectFetch(file);
	}

	@Override
	public IpfsFile getLastRootElement()
	{
		return _channelData.lastPublishedIndex();
	}

	@Override
	public IpfsKey getPublicKey()
	{
		return _publicKey;
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		Assert.assertTrue(null != keyToResolve);
		return _scheduler.resolvePublicKey(keyToResolve);
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _scheduler.getSizeInBytes(cid);
	}

	@Override
	public FuturePublish republishIndex()
	{
		IpfsFile lastRoot = _channelData.lastPublishedIndex();
		return _scheduler.publishIndex(_keyName, _publicKey, lastRoot);
	}

	@Override
	public ConcurrentTransaction openConcurrentTransaction()
	{
		return new ConcurrentTransaction(_scheduler, _pinCache.snapshotPinnedSet());
	}

	// ----- Writing methods -----
	@Override
	public IFolloweeWriting writableFolloweeData()
	{
		Assert.assertTrue(null != _readWrite);
		// We will want to write this back.
		_writeFollowIndex = true;
		return _followeeData;
	}

	@Override
	public void writePrefs(PrefsData prefs)
	{
		Assert.assertTrue(null != _readWrite);
		_readWrite.writeGlobalPrefs(prefs);
	}

	@Override
	public IpfsFile uploadAndPin(InputStream dataToSave) throws IpfsConnectionException
	{
		FutureSave save = _scheduler.saveStream(dataToSave);
		IpfsFile hash = save.get();
		_pinCache.addRef(hash);
		return hash;
	}

	@Override
	public IpfsFile uploadIndexAndUpdateTracking(StreamIndex streamIndex) throws IpfsConnectionException
	{
		Assert.assertTrue(null != _readWrite);
		byte[] serializedIndex;
		try
		{
			serializedIndex = GlobalData.serializeIndex(streamIndex);
		}
		catch (SizeConstraintException e)
		{
			// We created this as well-formed so it can't be this large.
			throw Assert.unexpected(e);
		}
		FutureSave save = _scheduler.saveStream(new ByteArrayInputStream(serializedIndex));
		IpfsFile hash = save.get();
		_pinCache.addRef(hash);
		_channelData.setLastPublishedIndex(hash);
		_writeChannelData = true;
		return hash;
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		boolean shouldPin = !_pinCache.isPinned(cid);
		_pinCache.addRef(cid);
		FuturePin pin = null;
		if (shouldPin)
		{
			pin = _scheduler.pin(cid);
		}
		else
		{
			// If we decided that this was already pinned, just return an already completed pin.
			pin = new FuturePin(cid);
			pin.success();
		}
		return pin;
	}

	@Override
	public void unpin(IpfsFile cid) throws IpfsConnectionException
	{
		_pinCache.delRef(cid);
		boolean shouldUnpin = !_pinCache.isPinned(cid);
		if (shouldUnpin)
		{
			_scheduler.unpin(cid).get();
		}
	}

	@Override
	public FuturePublish beginIndexPublish(IpfsFile indexRoot)
	{
		return _scheduler.publishIndex(_keyName, _publicKey, indexRoot);
	}

	@Override
	public void close()
	{
		// We always assume the pin cache is being written.
		if (null != _readWrite)
		{
			_readWrite.writeGlobalPinCache(_pinCache);
		}
		if (_writeFollowIndex)
		{
			_readWrite.writeFollowIndex(_followeeData);
		}
		if (_writeChannelData)
		{
			_readWrite.writeLocalIndex(_channelData);
		}
		// The read/write references are the same, when both present, but read-only is always present so close it.
		_readOnly.close();
	}

	@Override
	public void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins)
	{
		// First, walk the map of changed reference counts, adding and removing references where listed.
		for (Map.Entry<IpfsFile, Integer> entry: changedPinCounts.entrySet())
		{
			IpfsFile cid = entry.getKey();
			int countToChange = entry.getValue();
			while (countToChange > 0)
			{
				countToChange -= 1;
				_pinCache.addRef(cid);
			}
			while (countToChange < 0)
			{
				countToChange += 1;
				_pinCache.delRef(cid);
			}
			Assert.assertTrue(0 == countToChange);
			_rationalizeUnpin(cid);
		}
		// Now, walk the set of false pins, requesting that the network unpin them if they aren't referenced.
		for (IpfsFile pin : falsePins)
		{
			_rationalizeUnpin(pin);
		}
	}

	@Override
	public String getDirectFetchUrlRoot()
	{
		return _sharedConnection.directFetchUrlRoot();
	}


	private void _rationalizeUnpin(IpfsFile cid)
	{
		if (!_pinCache.isPinned(cid))
		{
			try
			{
				_scheduler.unpin(cid).get();
			}
			catch (IpfsConnectionException e)
			{
				// This is non-fatal but a concern.
				_logger.logError("Failed to unpin " + cid + ": " + e.getLocalizedMessage());
			}
		}
	}
}
