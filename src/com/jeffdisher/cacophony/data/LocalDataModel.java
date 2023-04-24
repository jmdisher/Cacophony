package com.jeffdisher.cacophony.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
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
	private static final byte LOCAL_CONFIG_VERSION_NUMBER = 2;

	private static final String V2_FINAL_LOG = "opcodes_0.final.gzlog";

	/**
	 * Verifies the on-disk data model, creating it if it isn't already present.
	 * Loads the on-disk model into memory, returning this representation.
	 * 
	 * @param fileSystem The file system where the data lives.
	 * @param scheduler The scheduler for fetching network resources.
	 * @param ipfsConnectionString The string describing the API server end-point.
	 * @param keyName The name of the IPFS key to use for this user.
	 * @param assertConsistent If true, will assert that the pin cache is consistent with the referencing data.
	 */
	public static LocalDataModel verifiedAndLoadedModel(IConfigFileSystem fileSystem, INetworkScheduler scheduler, String ipfsConnectionString, String keyName, boolean assertConsistent) throws UsageException
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
				_writeToDisk(fileSystem
						, ChannelData.create(ipfsConnectionString, keyName)
						, PrefsData.defaultPrefs()
						, PinCacheData.createEmpty()
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
		try (InputStream opcodeLog = fileSystem.readAtomicFile(V2_FINAL_LOG))
		{
			if (null == opcodeLog)
			{
				throw new UsageException("Local storage opcode log file is missing");
			}
			
			ProjectionBuilder.Projections projections = ProjectionBuilder.buildProjectionsFromOpcodeStream(opcodeLog);
			ChannelData channelData = projections.channel();
			FolloweeData followees = projections.followee();
			// We build the pin cache as a projection of our other data about the home user and followee data.
			// The on-disk pin cache is now only used to find leaked pin from older versions of the software the user might have run.
			// TODO:  Remove pin cache from next version of data model.
			PinCacheData pinCache = _buildPinCache(scheduler, channelData.lastPublishedIndex(), followees);
			PinCacheData diskPinCache = projections.pinCache();
			List<IpfsFile> incorrectlyPinned = diskPinCache.verifyMatch(pinCache);
			if (assertConsistent)
			{
				// The verification will return null if they are a perfect match.
				Assert.assertTrue(null == incorrectlyPinned);
			}
			else if (null != incorrectlyPinned)
			{
				// We are going to proceed with the derived cache so we need to unpin the extraneous references in the on-disk version.
				// NOTE:  This is NOT expected and is only present in some older data models do to an old bug where meta-data wasn't correctly unpinned.
				for (IpfsFile unpin : incorrectlyPinned)
				{
					System.err.println("REPAIRING: Unpin " + unpin);
					scheduler.unpin(unpin);
				}
				// Force the write-back since we needed to correct the model and have updated the network.
				try
				{
					_writeToDisk(fileSystem
							, channelData
							, projections.prefs()
							, pinCache
							, followees
					);
				}
				catch (IOException e)
				{
					// We don't expect a failure to write to local storage.
					throw Assert.unexpected(e);
				}
			}
			return new LocalDataModel(fileSystem
					, channelData
					, projections.prefs()
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

	private static PinCacheData _buildPinCache(INetworkScheduler scheduler, IpfsFile lastRootElement, FolloweeData followees)
	{
		PinCacheBuilder builder = new PinCacheBuilder(scheduler);
		if (null != lastRootElement)
		{
			builder.addHomeUser(lastRootElement);
			for (IpfsKey key : followees.getAllKnownFollowees())
			{
				builder.addFollowee(followees.getLastFetchedRootForFollowee(key), followees.snapshotAllElementsForFollowee(key));
			}
		}
		return builder.finish();
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
				, _globalPinCache
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
			, PinCacheData globalPinCache
			, FolloweeData followIndex
	) throws IOException
	{
		// We will serialize directly to the file.  If there are any exceptions, we won't reach the commit.
		try (IConfigFileSystem.AtomicOutputStream atomic = fileSystem.writeAtomicFile(V2_FINAL_LOG))
		{
			try (ObjectOutputStream output = OpcodeContext.createOutputStream(atomic.getStream()))
			{
				localIndex.serializeToOpcodeStream(output);
				globalPrefs.serializeToOpcodeStream(output);
				globalPinCache.serializeToOpcodeStream(output);
				followIndex.serializeToOpcodeStream(output);
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
		public void closeWrite(ChannelData updateLocalIndex, PinCacheData updateGlobalPinCache, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs)
		{
			// Write-back the elements they provided (anything passed as null is unchanged).
			
			boolean somethingUpdated = false;
			if (null != updateLocalIndex)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_localIndex == updateLocalIndex);
				somethingUpdated = true;
			}
			if (null != updateGlobalPinCache)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue(_globalPinCache == updateGlobalPinCache);
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
