package com.jeffdisher.cacophony.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.jeffdisher.cacophony.data.local.v2.OpcodeContext;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.projection.ProjectionBuilder;
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

	private final IConfigFileSystem _fileSystem;
	private final ReadWriteLock _readWriteLock;

	private boolean _didLoadStorage;
	private ChannelData _localIndex;
	private PinCacheData _globalPinCache;
	private FolloweeData _followIndex;
	private PrefsData _globalPrefs;

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
	}

	/**
	 * Called during start-up to make sure that the storage model is consistent.  This means that it will create the
	 * storage, if it doesn't already exist, and will make sure that it has a valid shape and can be used.
	 * 
	 * @param ipfsConnectionString The connection string to set in the channel config, if it needs to be created.
	 * @param keyName The IPFS key name to set in the channel config, if it needs to be created.
	 * @throws UsageException The data model couldn't be created or was inconsistent.
	 */
	public void verifyStorageConsistency(String ipfsConnectionString, String keyName) throws UsageException
	{
		// If the config doesn't exist, create it with default values.
		if (!_fileSystem.doesConfigDirectoryExist())
		{
			boolean didCreate = _fileSystem.createConfigDirectory();
			if (!didCreate)
			{
				throw new UsageException("Failed to create config directory");
			}
			// Create the instance and populate it with default files - this will require grabbing the write lock.
			try (IReadWriteLocalData writing = _openForWrite())
			{
				writing.writeLocalIndex(ChannelData.create(ipfsConnectionString, keyName));
				writing.writeGlobalPrefs(PrefsData.defaultPrefs());
				writing.writeGlobalPinCache(PinCacheData.createEmpty());
				writing.writeFollowIndex(FolloweeData.createEmpty());
			}
		}
		
		// Now that the data exists, validate its consistency.
		byte[] data = _fileSystem.readTrivialFile(VERSION_FILE);
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
			
			// We know that this is called immediately after creating the config so there should be an opcode log file.
			try (InputStream opcodeLog = _fileSystem.readAtomicFile(V2_FINAL_LOG))
			{
				if (null == opcodeLog)
				{
					throw new UsageException("Local storage opcode log file is missing");
				}
			}
			catch (IOException e)
			{
				// Close exception not expected.
				throw Assert.unexpected(e);
			}
		}
		else
		{
			// This file needs to exist.
			throw new UsageException("Version file missing");
		}
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
		if (!_didLoadStorage)
		{
			_loadAllFiles();
			_didLoadStorage = true;
		}
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


	private void _loadAllFiles()
	{
		try (InputStream input = _fileSystem.readAtomicFile(V2_FINAL_LOG))
		{
			// Note that this is null during initial creation.
			if (null != input)
			{
				ProjectionBuilder.Projections projections = ProjectionBuilder.buildProjectionsFromOpcodeStream(input);
				_localIndex = projections.channel();
				_globalPrefs = projections.prefs();
				_globalPinCache = projections.pinCache();
				_followIndex = projections.followee();
			}
		}
		catch (IOException e)
		{
			// We have no way to handle this failure.
			throw Assert.unexpected(e);
		}
	}

	private void _flushStateToStream() throws IOException
	{
		// Make sure that the state was actually loaded or set.
		// This is only not enforced by the loading mechanism in the case of a new channel, where all storage must be
		// explicitly created.
		Assert.assertTrue(_didLoadStorage);
		Assert.assertTrue(null != _localIndex);
		Assert.assertTrue(null != _globalPrefs);
		Assert.assertTrue(null != _globalPinCache);
		Assert.assertTrue(null != _followIndex);
		
		// We will serialize directly to the file.  If there are any exceptions, we won't reach the commit.
		try (IConfigFileSystem.AtomicOutputStream atomic = _fileSystem.writeAtomicFile(V2_FINAL_LOG))
		{
			try (ObjectOutputStream output = OpcodeContext.createOutputStream(atomic.getStream()))
			{
				_localIndex.serializeToOpcodeStream(output);
				_globalPrefs.serializeToOpcodeStream(output);
				_globalPinCache.serializeToOpcodeStream(output);
				_followIndex.serializeToOpcodeStream(output);
			}
			atomic.commit();
		}
		
		// Update the version file.
		_fileSystem.writeTrivialFile(VERSION_FILE, new byte[] { LOCAL_CONFIG_VERSION_NUMBER });
	}

	private IReadWriteLocalData _openForWrite()
	{
		Lock lock = _readWriteLock.writeLock();
		lock.lock();
		if (!_didLoadStorage)
		{
			_loadAllFiles();
			_didLoadStorage = true;
		}
		return LoadedStorage.openReadWrite(new WriteLock(lock), _localIndex, _globalPinCache, _followIndex, _globalPrefs);
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
				Assert.assertTrue((null == _localIndex) || (_localIndex == updateLocalIndex));
				_localIndex = updateLocalIndex;
				somethingUpdated = true;
			}
			if (null != updateGlobalPinCache)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue((null == _globalPinCache) || (_globalPinCache == updateGlobalPinCache));
				_globalPinCache = updateGlobalPinCache;
				somethingUpdated = true;
			}
			if (null != updateFollowIndex)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue((null == _followIndex) || (_followIndex == updateFollowIndex));
				_followIndex = updateFollowIndex;
				somethingUpdated = true;
			}
			if (null != updateGlobalPrefs)
			{
				// We can't change the instance - this is just to signify it may have changed.
				Assert.assertTrue((null == _globalPrefs) || (_globalPrefs == updateGlobalPrefs));
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
			
			_lock.unlock();
		}
	}
}
