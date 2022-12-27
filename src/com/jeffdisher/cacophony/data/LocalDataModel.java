package com.jeffdisher.cacophony.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.projection.ProjectionBuilder;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * An abstraction over most of the local data describing information required to manage the channel which isn't directly
 * on IPFS.  This data typically describes the data on IPFS or information the channel needs to know how to operate on
 * IPFS.
 * Interactions with the data store are done using explicitly read-only or read-write contexts.
 */
public class LocalDataModel
{
	private static final String VERSION_FILE = "version";
	private static final byte LOCAL_CONFIG_VERSION_NUMBER = 2;

	private static final byte V1 = 1;
	private static final String V1_INDEX_FILE = "index1.dat";
	private static final String V1_GLOBAL_PREFS_FILE = "global_prefs1.dat";
	private static final String V1_GLOBAL_PIN_CACHE_FILE = "global_pin_cache1.dat";
	private static final String V1_FOLLOWING_INDEX_FILE = "following_index1.dat";

	private static final String V2_FINAL_LOG = "opcodes_0.final.gzlog";

	private final IConfigFileSystem _fileSystem;
	private final ReadWriteLock _readWriteLock;

	private ChannelData _localIndex;
	private PinCacheData _globalPinCache;
	private FolloweeData _followIndex;
	private PrefsData _globalPrefs;

	private final ReentrantLock _cacheLock;
	private LocalRecordCache _lazyFolloweeCache;

	/**
	 * Loads the initial state of the data model from disk.  If the constructor returns without exception, then the
	 * local data model has been loaded and no more read-only operations related to it will run against storage.
	 * 
	 * @param fileSystem The file system where the data lives.
	 */
	public LocalDataModel(IConfigFileSystem fileSystem)
	{
		_fileSystem = fileSystem;
		_readWriteLock = new ReentrantReadWriteLock();
		
		// Setup the lock we will use to gate access to, and creation of, the lazy followee cache.
		_cacheLock = new ReentrantLock();
	}

	/**
	 * Called during start-up to make sure that the storage model is consistent.  A consistent model either doesn't
	 * exist or does exist and contains all files, updated to the latest version of the storage.
	 * If the storage is inconsistent, it will be repaired (if possible) or an exception will be thrown.
	 * 
	 * @throws UsageException The data model was inconsistent and couldn't be repaired.
	 */
	public void verifyStorageConsistency() throws UsageException
	{
		if (_fileSystem.doesConfigDirectoryExist())
		{
			try
			{
				// The config directory exists, so make sure that the version file is there.
				InputStream versionStream = _fileSystem.readConfigFile(VERSION_FILE);
				if (null != versionStream)
				{
					// The version file exists so just make sure it is what we expect.
					try (versionStream)
					{
						byte[] data = versionStream.readAllBytes();
						byte version = (1 == data.length)
								? data[0]
								: 0
						;
						if (LOCAL_CONFIG_VERSION_NUMBER == version)
						{
							// Current version, do nothing special.
						}
						else if (V1 == version)
						{
							// Old version, migrate the data.
							_migrateData();
						}
						else
						{
							// Unknown.
							throw new UsageException("Version data incorrect");
						}
					}
				}
				else
				{
					// This file needs to exist.
					throw new UsageException("Version file missing");
				}
			}
			catch (IOException e)
			{
				// Not expected.
				throw Assert.unexpected(e);
			}
		}
		else
		{
			// If the directory doesn't, this is valid (means that no channel has been created).
		}
	}

	/**
	 * Opens a read-only local data context.  This will access a shared read lock so no mutable operations can begin
	 * before this is closed.
	 * 
	 * @return The interface for issuing read-only operations against the storage.
	 * @throws VersionException If the version file was an unknown number or was missing when data exists.
	 */
	public IReadOnlyLocalData openForRead() throws VersionException
	{
		Lock lock = _readWriteLock.readLock();
		lock.lock();
		_loadAllFiles();
		return LoadedStorage.openReadOnly(this, new ReadLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs);
	}

	/**
	 * Called by the IReadOnlyLocalData implementation when it is closed.
	 * 
	 * @param lock The read lock originally given to the read context.
	 */
	public void closeRead(ReadLock lock)
	{
		lock.lock.unlock();
	}

	/**
	 * Opens a read-write local data context.  This will access an exclusive write lock so no other reading or writing
	 * operations can begin before this is closed.
	 * 
	 * @return The interface for issuing read-write operations against the storage.
	 * @throws VersionException If the version file was an unknown number or was missing when data exists.
	 */
	public IReadWriteLocalData openForWrite() throws VersionException
	{
		Lock lock = _readWriteLock.writeLock();
		lock.lock();
		_loadAllFiles();
		return LoadedStorage.openReadWrite(this, new WriteLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs);
	}

	/**
	 * Called by the IReadWriteLocalData implementation when it is closed.  Any of the non-null parameters passed back
	 * will be written to disk before the lock finishes being released.
	 * 
	 * @param lock The write lock originally given to the write context.
	 * @param updateLocalIndex Non-null if this should be saved as the new LocalIndex.
	 * @param updateGlobalPinCache Non-null if this should be saved as the new GlobalPinCache.
	 * @param updateFollowIndex Non-null if this should be saved as the new FollowIndex.
	 * @param updateGlobalPrefs Non-null if this should be saved as the new GlobalPrefs.
	 */
	public void closeWrite(WriteLock lock, ChannelData updateLocalIndex, PinCacheData updateGlobalPinCache, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs)
	{
		// Write-back the elements they provided (anything passed as null is unchanged).
		
		boolean somethingUpdated = false;
		if (null != updateLocalIndex)
		{
			_localIndex = updateLocalIndex;
			somethingUpdated = true;
		}
		if (null != updateGlobalPinCache)
		{
			_globalPinCache = updateGlobalPinCache;
			// Updating the _globalPinCache invalidates the _lazyFolloweeCache (this lock is redundant in this function
			//  but is the right pattern).
			// NOTE:  This isn't done on _followIndex since the cases where it changes in ways which would break the 
			//  cache are also the cases where _globalPinCache is changed.  Beyond that, changes to this local stream
			//  will NOT change _followIndex but do change _globalPinCache and should invalidate the cache.
			// The only times where this will be extraneous are when the pin cache changes are only for unrelated
			//  meta-data.
			_cacheLock.lock();
			try
			{
				_lazyFolloweeCache = null;
			}
			finally
			{
				_cacheLock.unlock();
			}
			somethingUpdated = true;
		}
		if (null != updateFollowIndex)
		{
			_followIndex = updateFollowIndex;
			somethingUpdated = true;
		}
		if (null != updateGlobalPrefs)
		{
			_globalPrefs = updateGlobalPrefs;
			somethingUpdated = true;
		}
		// Write the version if anything changed.
		if (somethingUpdated)
		{
			try
			{
				_flushStateToStream();
			}
			catch (IOException e)
			{
				// We don't really have a fall-back for these exceptions.
				throw Assert.unexpected(e);
			}
		}
		
		lock.lock.unlock();
	}

	public LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator)
	{
		_cacheLock.lock();
		try
		{
			if (null == _lazyFolloweeCache)
			{
				_lazyFolloweeCache = cacheGenerator.get();
			}
			// Note that this can still be null if there was a connection error during generation.
			return _lazyFolloweeCache;
		}
		finally
		{
			_cacheLock.unlock();
		}
	}

	/**
	 * Drops all the internal caches, forcing them to be lazily reloaded when next needed.  The only real reason for
	 * this is for unit tests which may run into problems due to accidentally shared state.
	 */
	public void dropAllCaches()
	{
		_localIndex = null;
		_globalPinCache = null;
		_followIndex = null;
		_globalPrefs = null;
		
		_cacheLock.lock();
		try
		{
			_lazyFolloweeCache = null;
		}
		finally
		{
			_cacheLock.unlock();
		}
	}


	private void _loadAllFiles()
	{
		try (InputStream input = _fileSystem.readConfigFile(V2_FINAL_LOG))
		{
			// Note that this is null during initial creation.
			if (null != input)
			{
				ProjectionBuilder.Projections projections = ProjectionBuilder.buildProjectionsFromOpcodeStream(_fileSystem.readConfigFile(V2_FINAL_LOG));
				_localIndex = projections.channel();
				_globalPrefs = projections.prefs();
				_globalPinCache = projections.pinCache();
				_followIndex = projections.followee();
			}
			else
			{
				// We can't create a default _localIndex, so we will need to assume that we are creating a new channel.
				_globalPrefs = PrefsData.defaultPrefs();
				_globalPinCache = PinCacheData.createEmpty();
				_followIndex = FolloweeData.createEmpty();
			}
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
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

	private void _migrateData() throws IOException, UsageException
	{
		// Read the original files.
		LocalIndex localIndex = _readFile(V1_INDEX_FILE, LocalIndex.class);
		// If the original file is missing, this is a UsageException.
		if (null == localIndex)
		{
			throw new UsageException("Missing index file");
		}
		ChannelData channelData = ChannelData.buildOnIndex(localIndex);
		
		PinCacheData pinCacheData = null;
		InputStream pinStream = _fileSystem.readConfigFile(V1_GLOBAL_PIN_CACHE_FILE);
		if (null != pinStream)
		{
			try (pinStream)
			{
				// We shouldn't have a followee cache, yet, so no need to lock and invalidate.
				Assert.assertTrue(null == _lazyFolloweeCache);
				pinCacheData = PinCacheData.buildOnCache(GlobalPinCache.fromStream(pinStream));
			}
			Assert.assertTrue(null != pinCacheData);
		}
		else
		{
			pinCacheData = PinCacheData.createEmpty();
		}
		
		FolloweeData followeeData = null;
		InputStream followeeStream = _fileSystem.readConfigFile(V1_FOLLOWING_INDEX_FILE);
		if (null != followeeStream)
		{
			try (followeeStream)
			{
				followeeData = FolloweeData.buildOnIndex(FollowIndex.fromStream(followeeStream));
			}
		}
		else
		{
			followeeData = FolloweeData.createEmpty();
		}
		
		GlobalPrefs prefs = _readFile(V1_GLOBAL_PREFS_FILE, GlobalPrefs.class);
		PrefsData prefsData = PrefsData.defaultPrefs();
		if (null != prefs)
		{
			prefsData.videoEdgePixelMax = prefs.videoEdgePixelMax();
			prefsData.followCacheTargetBytes = prefs.followCacheTargetBytes();
		}
		
		// Set the ivars.
		_localIndex = channelData;
		_globalPinCache = pinCacheData;
		_followIndex = followeeData;
		_globalPrefs = prefsData;
		
		// Write the projections to the new stream.
		_flushStateToStream();
	}

	private void _flushStateToStream() throws IOException
	{
		// We will first serialize to memory, before we re-write the on-disk file.
		// This will protect us from bugs, but not power loss.
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = OpcodeContext.createOutputStream(bytes))
		{
			_localIndex.serializeToOpcodeStream(output);
			_globalPrefs.serializeToOpcodeStream(output);
			_globalPinCache.serializeToOpcodeStream(output);
			_followIndex.serializeToOpcodeStream(output);
		}
		bytes.close();
		
		byte[] serialized = bytes.toByteArray();
		try (OutputStream output = _fileSystem.writeConfigFile(V2_FINAL_LOG))
		{
			output.write(serialized);
		}
		
		// Update the version file.
		try (OutputStream versionStream = _fileSystem.writeConfigFile(VERSION_FILE))
		{
			versionStream.write(new byte[] { LOCAL_CONFIG_VERSION_NUMBER });
		}
	}

	public static record ReadLock(Lock lock) {};
	public static record WriteLock(Lock lock) {};
}
