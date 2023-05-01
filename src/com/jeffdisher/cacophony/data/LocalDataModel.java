package com.jeffdisher.cacophony.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.OpcodeContext;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.PinCacheBuilder;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.projection.ProjectionBuilder;
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

	private static final byte V2 = 2;
	private static final String V2_FINAL_LOG = "opcodes_0.final.gzlog";
	private static final String V3_LOG = "opcodes.v3.gzlog";

	/**
	 * Verifies the on-disk data model, creating it if it isn't already present.
	 * Loads the on-disk model into memory, returning this representation.
	 * 
	 * @param fileSystem The file system where the data lives.
	 * @param scheduler The scheduler for fetching network resources.
	 */
	public static LocalDataModel verifiedAndLoadedModel(IConfigFileSystem fileSystem, INetworkScheduler scheduler) throws UsageException
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
			else if (V2 == version)
			{
				// The V2_FINAL_LOG version - migrate the data.
				_migrateData(fileSystem, scheduler);
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
			);
			OpcodeCodec.decodeWholeStream(opcodeLog, context);
			ChannelData channels = context.channelData();
			PrefsData prefs = context.prefs();
			FolloweeData followees = context.followees();
			Set<String> channelKeyNames = channels.getKeyNames();
			IpfsFile[] homeRoots = channelKeyNames.stream()
					.map((String channelKeyName) -> channels.getLastPublishedIndex(channelKeyName))
					.toArray((int size) -> new IpfsFile[size])
			;
			PinCacheData pinCache = _buildPinCache(scheduler, homeRoots, followees);
			return new LocalDataModel(fileSystem
					, channels
					, prefs
					, pinCache
					, followees
			);
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
		}
	}

	private static PinCacheData _buildPinCache(INetworkScheduler scheduler, IpfsFile[] homeRootElements, FolloweeData followees)
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
		return builder.finish();
	}

	private static void _migrateData(IConfigFileSystem fileSystem, INetworkScheduler scheduler)
	{
		try (InputStream opcodeLog = fileSystem.readAtomicFile(V2_FINAL_LOG))
		{
			// We are only here for the version upgrade so the data must be here.
			Assert.assertTrue(null != opcodeLog);
			
			ProjectionBuilder.Projections projections = ProjectionBuilder.buildProjectionsFromOpcodeStream(scheduler, opcodeLog);
			ChannelData channelData = projections.channel();
			FolloweeData followees = projections.followee();
			
			Set<String> channelKeyNames = channelData.getKeyNames();
			// We expect precisely one channel when migrating data from version 2.
			Assert.assertTrue(1 == channelKeyNames.size());
			String keyName = channelKeyNames.iterator().next();
			Assert.assertTrue(keyName.length() > 0);
			IpfsFile lastIndex = channelData.getLastPublishedIndex(keyName);
			Assert.assertTrue(null != lastIndex);
			
			// We build the pin cache as a projection of our other data about the home user and followee data.
			IpfsFile[] homeRoots = (null != lastIndex)
					? new IpfsFile[] { lastIndex }
					: new IpfsFile[0]
			;
			PinCacheData pinCache = _buildPinCache(scheduler, homeRoots, followees);
			PinCacheData diskPinCache = projections.pinCache();
			List<IpfsFile> incorrectlyPinned = diskPinCache.verifyMatch(pinCache);
			// The verification will return null if they are a perfect match.
			Assert.assertTrue(null == incorrectlyPinned);
			
			// Also update the drafts, since they no longer use Java serialization in V3.
			DraftManager manager = new DraftManager(fileSystem.getDraftsTopLevelDirectory());
			manager.migrateDrafts();
			
			// Now, write-back the data.
			_writeToDisk(fileSystem, channelData, projections.prefs(), followees);
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
		}
	}


	private final IConfigFileSystem _fileSystem;
	private final ChannelData _localIndex;
	private final PrefsData _globalPrefs;
	private final PinCacheData _globalPinCache;
	private final FolloweeData _followIndex;
	private final ReadWriteLock _readWriteLock;

	private LocalDataModel(IConfigFileSystem fileSystem
			, ChannelData localIndex
			, PrefsData globalPrefs
			, PinCacheData globalPinCache
			, FolloweeData followIndex
	)
	{
		_fileSystem = fileSystem;
		_localIndex = localIndex;
		_globalPrefs = globalPrefs;
		_globalPinCache = globalPinCache;
		_followIndex = followIndex;
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
		lock.lock();
		return LoadedStorage.openReadOnly(new ReadLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs);
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
		);
	}

	private IReadWriteLocalData _openForWrite()
	{
		Lock lock = _readWriteLock.writeLock();
		lock.lock();
		return LoadedStorage.openReadWrite(new WriteLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs);
	}

	private static void _writeToDisk(IConfigFileSystem fileSystem
			, ChannelData localIndex
			, PrefsData globalPrefs
			, FolloweeData followIndex
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
		public void closeWrite(ChannelData updateLocalIndex, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs)
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
}
