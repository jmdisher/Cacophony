package com.jeffdisher.cacophony.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
	 * Called during start-up to make sure that the storage model is consistent.  A consistent model MUST exist and
	 * contains all files, updated to the latest version of the storage.
	 * If the storage is inconsistent, it will be repaired (if possible) or an exception will be thrown.
	 * 
	 * @throws UsageException The data model was inconsistent and couldn't be repaired.
	 */
	public void verifyStorageConsistency() throws UsageException
	{
		// We know that this is called immediately after creating any missing config.
		Assert.assertTrue(_fileSystem.doesConfigDirectoryExist());
		try (InputStream versionStream = _fileSystem.readConfigFile(VERSION_FILE))
		{
			if (null != versionStream)
			{
				// The version file exists so just make sure it is what we expect.
				byte[] data = versionStream.readAllBytes();
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
				try (InputStream opcodeLog = _fileSystem.readConfigFile(V2_FINAL_LOG))
				{
					if (null == opcodeLog)
					{
						throw new UsageException("Local storage opcode log file is missing");
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
	 */
	public IReadWriteLocalData openForWrite()
	{
		Lock lock = _readWriteLock.writeLock();
		lock.lock();
		if (!_didLoadStorage)
		{
			_loadAllFiles();
			_didLoadStorage = true;
		}
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
		
		lock.lock.unlock();
	}

	/**
	 * Drops all the internal caches, forcing them to be lazily reloaded when next needed.  The only real reason for
	 * this is for unit tests which may run into problems due to accidentally shared state.
	 */
	public void dropAllCaches()
	{
		_didLoadStorage = false;
		_localIndex = null;
		_globalPinCache = null;
		_followIndex = null;
		_globalPrefs = null;
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
