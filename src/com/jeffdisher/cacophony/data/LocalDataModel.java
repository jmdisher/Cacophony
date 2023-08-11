package com.jeffdisher.cacophony.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContext;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.PinCacheBuilder;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
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
	private static final byte LOCAL_CONFIG_VERSION_NUMBER = 3;

	private static final String V3_LOG = "opcodes.v3.gzlog";

	/**
	 * Verifies the on-disk data model, creating it if it isn't already present.
	 * Loads the on-disk model into memory, returning this representation.
	 * 
	 * @param stats The locking stats object which will be notified about lock acquisition times.
	 * @param fileSystem The file system where the data lives.
	 * @param scheduler The scheduler for fetching network resources.
	 */
	public static LocalDataModel verifiedAndLoadedModel(ILockingStats stats, IConfigFileSystem fileSystem, INetworkScheduler scheduler) throws UsageException
	{
		// If the config doesn't exist, create it with default values.
		if (!fileSystem.doesConfigDirectoryExist())
		{
			boolean didCreate = fileSystem.createConfigDirectory();
			if (!didCreate)
			{
				throw new UsageException("Failed to create config directory");
			}
			// Write the initial files.
			try
			{
				ChannelData channelData = ChannelData.create();
				_writeToDisk(fileSystem
						, channelData
						, PrefsData.defaultPrefs()
						, FolloweeData.createEmpty()
						, new FavouritesCacheData()
						, new ExplicitCacheData()
				);
			}
			catch (IOException e)
			{
				// We don't expect a failure to write to local storage.
				throw Assert.unexpected(e);
			}
		}
		
		// Now that the data exists, validate its consistency.
		byte[] data = fileSystem.readTrivialFile(VERSION_FILE);
		if (null != data)
		{
			// The version file exists so just make sure it is what we expect.
			byte version = (1 == data.length)
					? data[0]
					: 0
			;
			if (LOCAL_CONFIG_VERSION_NUMBER == version)
			{
				// Current version, do nothing special.
			}
			else if (1 == version)
			{
				// We don't support version 1 so throw an exception describing how to update.
				throw new UsageException("Local storage version 1 cannot be migrated by this version.  Either delete the ~/.cacophony directory or try running with version 2.1.1, first.");
			}
			else if (2 == version)
			{
				// We don't support version 2 so throw an exception describing how to update.
				throw new UsageException("Local storage version 2 cannot be migrated by this version.  Either delete the ~/.cacophony directory or try running with version 3.1, first.");
			}
			else
			{
				// Unknown.
				throw new UsageException("Local storage version cannot be understood: " + version);
			}
		}
		else
		{
			// This file needs to exist.
			throw new UsageException("Version file missing");
		}
		
		// Now, load all the files so we can create the initial data model.
		try (InputStream opcodeLog = fileSystem.readAtomicFile(V3_LOG))
		{
			if (null == opcodeLog)
			{
				throw new UsageException("Local storage opcode log file is missing");
			}
			
			OpcodeContext context = new OpcodeContext(ChannelData.create()
					, PrefsData.defaultPrefs()
					, FolloweeData.createEmpty()
					, new FavouritesCacheData()
					, new ExplicitCacheData()
			);
			OpcodeCodec.decodeWholeStream(opcodeLog, context);
			ChannelData channels = context.channelData();
			PrefsData prefs = context.prefs();
			FolloweeData followees = context.followees();
			FavouritesCacheData favouritesCache = context.favouritesCache();
			ExplicitCacheData explicitCache = context.explicitCache();
			Set<String> channelKeyNames = channels.getKeyNames();
			IpfsFile[] homeRoots = channelKeyNames.stream()
					.map((String channelKeyName) -> channels.getLastPublishedIndex(channelKeyName))
					.toArray((int size) -> new IpfsFile[size])
			;
			PinCacheData pinCache = _buildPinCache(scheduler, homeRoots, followees, favouritesCache, explicitCache);
			return new LocalDataModel(stats
					, fileSystem
					, channels
					, prefs
					, pinCache
					, followees
					, favouritesCache
					, explicitCache
			);
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
		}
	}

	private static PinCacheData _buildPinCache(INetworkScheduler scheduler, IpfsFile[] homeRootElements, FolloweeData followees, FavouritesCacheData favouritesCache, ExplicitCacheData explicitCache)
	{
		PinCacheBuilder builder = new PinCacheBuilder(scheduler);
		for (IpfsFile homeRoot : homeRootElements)
		{
			builder.addHomeUser(homeRoot);
		}
		for (IpfsKey key : followees.getAllKnownFollowees())
		{
			builder.addFollowee(followees.getLastFetchedRootForFollowee(key), followees.snapshotAllElementsForFollowee(key));
		}
		builder.addFavourites(favouritesCache);
		builder.addExplicitCache(explicitCache);
		return builder.finish();
	}


	private final ILockingStats _stats;
	private final IConfigFileSystem _fileSystem;
	private final ChannelData _localIndex;
	private final PrefsData _globalPrefs;
	private final PinCacheData _globalPinCache;
	private final FolloweeData _followIndex;
	private final FavouritesCacheData _favouritesCache;
	private final ExplicitCacheData _explicitCache;
	private final ReadWriteLock _readWriteLock;

	private LocalDataModel(ILockingStats stats
			, IConfigFileSystem fileSystem
			, ChannelData localIndex
			, PrefsData globalPrefs
			, PinCacheData globalPinCache
			, FolloweeData followIndex
			, FavouritesCacheData favouritesCache
			, ExplicitCacheData explicitCache
	)
	{
		_stats = stats;
		_fileSystem = fileSystem;
		_localIndex = localIndex;
		_globalPrefs = globalPrefs;
		_globalPinCache = globalPinCache;
		_followIndex = followIndex;
		_favouritesCache = favouritesCache;
		_explicitCache = explicitCache;
		_readWriteLock = new ReentrantReadWriteLock();
	}

	/**
	 * Opens a read-only local data context.  This will access a shared read lock so no mutable operations can begin
	 * before this is closed.
	 * 
	 * @return The interface for issuing read-only operations against the storage.
	 */
	public IReadOnlyLocalData openForRead()
	{
		Lock lock = _readWriteLock.readLock();
		long startMillis = _stats.currentTimeMillis();
		lock.lock();
		long endMillis = _stats.currentTimeMillis();
		long deltaMillis = endMillis - startMillis;
		_stats.acquiredReadLock(deltaMillis);
		return LoadedStorage.openReadOnly(new ReadLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs, _favouritesCache, _explicitCache);
	}

	/**
	 * Opens a read-write local data context.  This will access an exclusive write lock so no other reading or writing
	 * operations can begin before this is closed.
	 * 
	 * @return The interface for issuing read-write operations against the storage.
	 */
	public IReadWriteLocalData openForWrite()
	{
		return _openForWrite();
	}


	private void _flushStateToStream() throws IOException
	{
		_writeToDisk(_fileSystem
				, _localIndex
				, _globalPrefs
				, _followIndex
				, _favouritesCache
				, _explicitCache
		);
	}

	private IReadWriteLocalData _openForWrite()
	{
		Lock lock = _readWriteLock.writeLock();
		long startMillis = _stats.currentTimeMillis();
		lock.lock();
		long endMillis = _stats.currentTimeMillis();
		long deltaMillis = endMillis - startMillis;
		_stats.acquiredWriteLock(deltaMillis);
		return LoadedStorage.openReadWrite(new WriteLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs, _favouritesCache, _explicitCache);
	}

	private static void _writeToDisk(IConfigFileSystem fileSystem
			, ChannelData localIndex
			, PrefsData globalPrefs
			, FolloweeData followIndex
			, FavouritesCacheData favouritesCache
			, ExplicitCacheData explicitCache
	) throws IOException
	{
		// We will serialize directly to the file.  If there are any exceptions, we won't reach the commit.
		try (IConfigFileSystem.AtomicOutputStream atomic = fileSystem.writeAtomicFile(V3_LOG))
		{
			try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(atomic.getStream()))
			{
				localIndex.serializeToOpcodeWriter(writer);
				globalPrefs.serializeToOpcodeWriter(writer);
				followIndex.serializeToOpcodeWriter(writer);
				favouritesCache.serializeToOpcodeWriter(writer);
				explicitCache.serializeToOpcodeWriter(writer);
			}
			atomic.commit();
		}
		
		// Update the version file.
		fileSystem.writeTrivialFile(VERSION_FILE, new byte[] { LOCAL_CONFIG_VERSION_NUMBER });
	}


	public class ReadLock implements LoadedStorage.UnlockRead
	{
		private final Lock _lock;
		public ReadLock(Lock lock)
		{
			_lock = lock;
		}
		@Override
		public void closeRead()
		{
			_lock.unlock();
		}
	}

	public class WriteLock implements LoadedStorage.UnlockWrite
	{
		private final Lock _lock;
		public WriteLock(Lock lock)
		{
			_lock = lock;
		}
		@Override
		public void closeWrite(ChannelData updateLocalIndex, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs, FavouritesCacheData updateFavouritesCache, ExplicitCacheData updatedExplicitCache)
		{
			// Write-back the elements they provided (anything passed as null is unchanged).
			
			boolean somethingUpdated = false;
			if (null != updateLocalIndex)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_localIndex == updateLocalIndex);
				somethingUpdated = true;
			}
			if (null != updateFollowIndex)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_followIndex == updateFollowIndex);
				somethingUpdated = true;
			}
			if (null != updateGlobalPrefs)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_globalPrefs == updateGlobalPrefs);
				somethingUpdated = true;
			}
			if (null != updateFavouritesCache)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_favouritesCache == updateFavouritesCache);
				somethingUpdated = true;
			}
			if (null != updatedExplicitCache)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_explicitCache == updatedExplicitCache);
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
			_lock.unlock();
		}
	}


	/**
	 * A simple mechanism for collecting stats around lock acquisition times within the LocalDataModel.
	 * This is intended to provide insights into unusually long lock times for later investigation.
	 */
	public static interface ILockingStats
	{
		/**
		 * @return The current time, in milliseconds.
		 */
		long currentTimeMillis();
		/**
		 * Called after acquiring the read lock.
		 * @param waitMillis The number of milliseconds the thread waited to acquire the lock.
		 */
		void acquiredReadLock(long waitMillis);
		/**
		 * Called after acquiring the write lock.
		 * @param waitMillis The number of milliseconds the thread waited to acquire the lock.
		 */
		void acquiredWriteLock(long waitMillis);
	}


	/**
	 * An empty implementation of LocalDataModel.ILockingStats which does nothing.  This is intended for use in tests
	 * and other environments where this information isn't meaningful.
	 */
	public static final LocalDataModel.ILockingStats NONE = new LocalDataModel.ILockingStats() {
		@Override
		public long currentTimeMillis()
		{
			return 0L;
		}
		@Override
		public void acquiredWriteLock(long waitMillis)
		{
		}
		@Override
		public void acquiredReadLock(long waitMillis)
		{
		}
	};
}
