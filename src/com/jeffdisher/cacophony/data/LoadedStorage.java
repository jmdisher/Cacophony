package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Implements the read and write access to the local storage.
 * While this is basically just a container which knows how to write-back its changes on close, it also does ensure that
 * there is no access attempts made after it is closed.
 */
public class LoadedStorage implements IReadWriteLocalData
{
	public static IReadOnlyLocalData openReadOnly(UnlockRead readLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		return new LoadedStorage(readLock, null, localIndex, globalPinCache, followIndex, globalPrefs);
	}

	public static IReadWriteLocalData openReadWrite(UnlockWrite writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		return new LoadedStorage(null, writeLock, localIndex, globalPinCache, followIndex, globalPrefs);
	}


	private final UnlockRead _readLock;
	private final UnlockWrite _writeLock;
	private boolean _isOpen;
	private ChannelData _localIndex;
	private boolean _changed_localIndex;
	private PinCacheData _globalPinCache;
	private boolean _changed_globalPinCache;
	private FolloweeData _followIndex;
	private boolean _changed_followIndex;
	private PrefsData _globalPrefs;
	private boolean _changed_globalPrefs;

	private LoadedStorage(UnlockRead readLock, UnlockWrite writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		_readLock = readLock;
		_writeLock = writeLock;
		_isOpen = true;
		
		_localIndex = localIndex;
		_globalPinCache = globalPinCache;
		_followIndex = followIndex;
		_globalPrefs = globalPrefs;
	}

	@Override
	public ChannelData readLocalIndex()
	{
		Assert.assertTrue(_isOpen);
		return _localIndex;
	}

	@Override
	public PrefsData readGlobalPrefs()
	{
		Assert.assertTrue(_isOpen);
		return _globalPrefs;
	}

	@Override
	public PinCacheData readGlobalPinCache()
	{
		Assert.assertTrue(_isOpen);
		return _globalPinCache;
	}

	@Override
	public FolloweeData readFollowIndex()
	{
		Assert.assertTrue(_isOpen);
		return _followIndex;
	}

	@Override
	public void close()
	{
		Assert.assertTrue(_isOpen);
		if (null != _writeLock)
		{
			_writeLock.closeWrite((_changed_localIndex ? _localIndex : null)
					, (_changed_globalPinCache ? _globalPinCache : null)
					, (_changed_followIndex ? _followIndex : null)
					, (_changed_globalPrefs ? _globalPrefs : null)
			);
		}
		else
		{
			_readLock.closeRead();
		}
		_isOpen = false;
	}

	@Override
	public void writeLocalIndex(ChannelData index)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_localIndex = index;
		_changed_localIndex = true;
	}

	@Override
	public void writeGlobalPrefs(PrefsData prefs)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_globalPrefs = prefs;
		_changed_globalPrefs = true;
	}

	@Override
	public void writeGlobalPinCache(PinCacheData pinCache)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_globalPinCache = pinCache;
		_changed_globalPinCache = true;
	}

	@Override
	public void writeFollowIndex(FolloweeData followIndex)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_followIndex = followIndex;
		_changed_followIndex = true;
	}


	/**
	 * Implemented by a read-only caller to be notified when the storage is closed.
	 */
	public interface UnlockRead
	{
		/**
		 * Called when the storage read lock should be released as the instance is now closed.
		 */
		void closeRead();
	}

	/**
	 * Implemented by a read-write caller to be notified when the storage is closed, and told what was updated.
	 */
	public interface UnlockWrite
	{
		/**
		 * Called when the storage read-write lock should be released as the instance is now closed.  Any of the
		 * non-null parameters passed back MUST be written to disk before the lock finishes being released.
		 * 
		 * @param updateLocalIndex Non-null if this should be saved as the new ChannelData.
		 * @param updateGlobalPinCache Non-null if this should be saved as the new PinCacheData.
		 * @param updateFollowIndex Non-null if this should be saved as the new FolloweeData.
		 * @param updateGlobalPrefs Non-null if this should be saved as the new PrefsData.
		 */
		void closeWrite(ChannelData updateLocalIndex, PinCacheData updateGlobalPinCache, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs);
	}
}
