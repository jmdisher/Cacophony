package com.jeffdisher.cacophony.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.data.local.IConfigFileSystem;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.data.local.v4.FolloweeLoader;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.logic.PinCacheBuilder;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
	private static final byte LOCAL_CONFIG_VERSION_NUMBER = 4;
	private static final String V4_LOG = "opcodes.v4.gzlog";

	private static final byte V3 = 3;
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
			else if (V3 == version)
			{
				// This version can be migrated.
				try
				{
					_migrateData(fileSystem, scheduler);
				}
				catch (IpfsConnectionException e)
				{
					// This was a network error during migration so we will throw this as usage that they should check their IPFS daemon.
					throw new UsageException("Migration aborted due to IPFS connection error.  Make sure that your IPFS daemon is running: " + e.getLocalizedMessage());
				}
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
		try (InputStream opcodeLog = fileSystem.readAtomicFile(V4_LOG))
		{
			if (null == opcodeLog)
			{
				throw new UsageException("Local storage opcode log file is missing");
			}
			
			FolloweeData followees = FolloweeData.createEmpty();
			OpcodeContext context = new OpcodeContext(ChannelData.create()
					, PrefsData.defaultPrefs()
					, new FolloweeLoader(followees)
					, new FavouritesCacheData()
					, new ExplicitCacheData()
			);
			OpcodeCodec.decodeWholeStream(opcodeLog, context);
			ChannelData channels = context.channelData();
			PrefsData prefs = context.prefs();
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

	private static void _migrateData(IConfigFileSystem fileSystem, INetworkScheduler scheduler) throws IpfsConnectionException
	{
		try (InputStream opcodeLog = fileSystem.readAtomicFile(V3_LOG))
		{
			Assert.assertTrue(null != opcodeLog);
			
			OpcodeContextV3 context = new OpcodeContextV3(ChannelData.create()
					, PrefsData.defaultPrefs()
					, FolloweeData.createEmpty()
					, new FavouritesCacheData()
					, new ExplicitCacheData()
					, new ArrayList<>()
			);
			OpcodeCodec.decodeWholeStreamV3(opcodeLog, context);
			ChannelData channels = context.channelData();
			PrefsData prefs = context.prefs();
			FolloweeData followees = context.followees();
			FavouritesCacheData favouritesCache = context.favouritesCache();
			ExplicitCacheData explicitCache = context.explicitCache();
			
			// Get all the home channel data.
			Set<String> channelKeyNames = channels.getKeyNames();
			IpfsFile[] homeRoots = channelKeyNames.stream()
					.map((String channelKeyName) -> channels.getLastPublishedIndex(channelKeyName))
					.toArray((int size) -> new IpfsFile[size])
			;
			
			// Look up all the data associated with the followees (since V3 only had partial data, but we need all the elements).
			// Since this is just one-time migration, we will fetch everything synchronously (for simplicity).
			try
			{
				for (IpfsKey followeeKey : followees.getAllKnownFollowees())
				{
					// Load the records element so we can access the raw list of posts.
					AbstractIndex index = scheduler.readData(followees.getLastFetchedRootForFollowee(followeeKey), AbstractIndex.DESERIALIZER).get();
					AbstractRecords records = scheduler.readData(index.recordsCid, AbstractRecords.DESERIALIZER).get();
					Map<IpfsFile, FollowingCacheElement> map = followees.snapshotAllElementsForFollowee(followeeKey);
					for (IpfsFile cid : records.getRecordList())
					{
						// We are just looking at these to add the trivial records (those without pinned leaves).
						if (!map.containsKey(cid))
						{
							followees.addElement(followeeKey, new FollowingCacheElement(cid, null, null, 0L));
						}
					}
				}
			}
			catch (FailedDeserializationException e)
			{
				// (note that all of the data we are referencing here is actually pinned, so these loads are safe).
				throw Assert.unexpected(e);
			}
			
			// We can now build the pin cache.
			PinCacheData pinCache = _buildPinCache(scheduler, homeRoots, followees, favouritesCache, explicitCache);
			
			// In preparation for the change to the explicit cache user info shape, rationalize all the user info unpins.
			List<FutureVoid> unpinFutures = new ArrayList<>();
			for (IpfsFile cid : context.unpinsToRationalize())
			{
				// This is something we previously had pinned but now we don't want to count the reference so unpin if it was the only reference.
				if (!pinCache.isPinned(cid))
				{
					unpinFutures.add(scheduler.unpin(cid));
				}
			}
			for (FutureVoid future : unpinFutures)
			{
				try
				{
					future.get();
				}
				catch (IpfsConnectionException e)
				{
					// For some reason (at least in 0.20.0), this seems to give us HTTP 500 but does actually correctly perform the unpin.
				}
				
			}
			
			_writeToDisk(fileSystem, channels, prefs, followees, favouritesCache, explicitCache);
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
		}
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
		return LoadedStorage.openReadOnly(new ReadLock(_stats, lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs, _favouritesCache, _explicitCache);
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
		return LoadedStorage.openReadWrite(new WriteLock(_stats, lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs, _favouritesCache, _explicitCache);
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
		try (IConfigFileSystem.AtomicOutputStream atomic = fileSystem.writeAtomicFile(V4_LOG))
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
		private final ILockingStats _stats;
		private final Lock _lock;
		public ReadLock(ILockingStats stats, Lock lock)
		{
			_stats = stats;
			_lock = lock;
		}
		@Override
		public void closeRead()
		{
			_stats.releasedReadLock();
			_lock.unlock();
		}
	}

	public class WriteLock implements LoadedStorage.UnlockWrite
	{
		private final ILockingStats _stats;
		private final Lock _lock;
		public WriteLock(ILockingStats stats, Lock lock)
		{
			_stats = stats;
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
			_stats.releasedWriteLock();
			_lock.unlock();
		}
	}


	/**
	 * A simple mechanism for collecting stats around lock acquisition times within the LocalDataModel.
	 * This is intended to provide insights into unusually long lock times for later investigation.
	 * TODO:  Remove this before final release.
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
		 * Called to release the read lock.
		 */
		void releasedReadLock();
		/**
		 * Called after acquiring the write lock.
		 * @param waitMillis The number of milliseconds the thread waited to acquire the lock.
		 */
		void acquiredWriteLock(long waitMillis);
		/**
		 * Called to release the write lock.
		 */
		void releasedWriteLock();
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
		public void releasedWriteLock()
		{
		}
		@Override
		public void acquiredReadLock(long waitMillis)
		{
		}
		@Override
		public void releasedReadLock()
		{
		}
	};
}
