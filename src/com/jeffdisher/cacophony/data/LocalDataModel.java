package com.jeffdisher.cacophony.data;

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
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
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
	private static final byte LOCAL_CONFIG_VERSION_NUMBER = 1;
	private static final String INDEX_FILE = "index1.dat";
	private static final String GLOBAL_PREFS_FILE = "global_prefs1.dat";
	private static final String GLOBAL_PIN_CACHE_FILE = "global_pin_cache1.dat";
	private static final String FOLLOWING_INDEX_FILE = "following_index1.dat";

	private final IConfigFileSystem _fileSystem;
	private final ReadWriteLock _readWriteLock;

	private LocalIndex _localIndex;
	private GlobalPinCache _globalPinCache;
	private FollowIndex _followIndex;
	private GlobalPrefs _globalPrefs;

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

	private void _eagerlyLoadFiles(IConfigFileSystem fileSystem) throws VersionException
	{
		// Note that we eagerly load the data files, so we can keep all the read/write capabilities within this class
		// and eagerly throw the version exception.
		// In the case where there is no version, we will just verify that nothing exists and will write the version
		// file when we first write anything.
		
		// We do require that the config directly at least exist.
		Assert.assertTrue(_fileSystem.doesConfigDirectoryExist());
		
		// Check if the version file exists (if not, no other files can exist).
		InputStream versionStream = fileSystem.readConfigFile(VERSION_FILE);
		if (null != versionStream)
		{
			// The version file exists so just make sure it is what we expect.
			try (versionStream)
			{
				byte[] data = versionStream.readAllBytes();
				if ((1 == data.length) && (LOCAL_CONFIG_VERSION_NUMBER == data[0]))
				{
					// This is a version we can understand so load whatever data there is.
					_loadAllFiles();
				}
				else
				{
					throw new VersionException("Local config is unknown version");
				}
			}
			catch (IOException e)
			{
				// We don't really have a fall-back for these exceptions.
				throw Assert.unexpected(e);
			}
		}
		else
		{
			// The version file is missing so check that nothing else is there.
			_loadAllFiles();
			if ((null != _localIndex) || (null != _globalPinCache) || (null != _followIndex) || (null != _globalPrefs))
			{
				throw new VersionException("Version file missing but data exists");
			}
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
		_eagerlyLoadFiles(_fileSystem);
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
		_eagerlyLoadFiles(_fileSystem);
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
	public void closeWrite(WriteLock lock, LocalIndex updateLocalIndex, GlobalPinCache updateGlobalPinCache, FollowIndex updateFollowIndex, GlobalPrefs updateGlobalPrefs)
	{
		// Write-back the elements they provided (anything passed as null is unchanged).
		
		boolean somethingUpdated = false;
		if (null != updateLocalIndex)
		{
			_localIndex = updateLocalIndex;
			_storeFile(INDEX_FILE, _localIndex);
			somethingUpdated = true;
		}
		if (null != updateGlobalPinCache)
		{
			_globalPinCache = updateGlobalPinCache;
			try (OutputStream stream = _fileSystem.writeConfigFile(GLOBAL_PIN_CACHE_FILE))
			{
				_globalPinCache.writeToStream(stream);
			}
			catch (IOException e)
			{
				// Failure not expected.
				throw Assert.unexpected(e);
			}
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
			try (OutputStream stream = _fileSystem.writeConfigFile(FOLLOWING_INDEX_FILE))
			{
				_followIndex.writeToStream(stream);
			}
			catch (IOException e)
			{
				// Failure not expected.
				throw Assert.unexpected(e);
			}
			somethingUpdated = true;
		}
		if (null != updateGlobalPrefs)
		{
			_globalPrefs = updateGlobalPrefs;
			_storeFile(GLOBAL_PREFS_FILE, _globalPrefs);
			somethingUpdated = true;
		}
		// Write the version if anything changed.
		if (somethingUpdated)
		{
			try (OutputStream versionStream = _fileSystem.writeConfigFile(VERSION_FILE))
			{
				versionStream.write(new byte[] { LOCAL_CONFIG_VERSION_NUMBER });
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


	private void _loadAllFiles()
	{
		if (null == _localIndex)
		{
			_localIndex = _readFile(INDEX_FILE, LocalIndex.class);
		}
		if (null == _globalPinCache)
		{
			InputStream pinStream = _fileSystem.readConfigFile(GLOBAL_PIN_CACHE_FILE);
			if (null != pinStream)
			{
				try (pinStream)
				{
					// We shouldn't have a followee cache, yet, so no need to lock and invalidate.
					Assert.assertTrue(null == _lazyFolloweeCache);
					_globalPinCache = GlobalPinCache.fromStream(pinStream);
					pinStream.close();
				}
				catch (IOException e)
				{
					// Failure on close not expected.
					throw Assert.unexpected(e);
				}
			}
		}
		if (null == _followIndex)
		{
			InputStream followeeStream = _fileSystem.readConfigFile(FOLLOWING_INDEX_FILE);
			if (null != followeeStream)
			{
				try (followeeStream)
				{
					_followIndex = FollowIndex.fromStream(followeeStream);
					followeeStream.close();
				}
				catch (IOException e)
				{
					// Failure on close not expected.
					throw Assert.unexpected(e);
				}
			}
		}
		if (null == _globalPrefs)
		{
			_globalPrefs = _readFile(GLOBAL_PREFS_FILE, GlobalPrefs.class);
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

	private <T> void _storeFile(String fileName, T object)
	{
		try (ObjectOutputStream stream = new ObjectOutputStream(_fileSystem.writeConfigFile(fileName)))
		{
			stream.writeObject(object);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
	}

	public static record ReadLock(Lock lock) {};
	public static record WriteLock(Lock lock) {};
}
