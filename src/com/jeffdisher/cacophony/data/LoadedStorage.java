package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
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
	/**
	 * Returns storage as a shared, read-only instance.
	 * 
	 * @param readLock The read lock to release when closing.
	 * @param localIndex The local index.
	 * @param globalPinCache The global pin cache.
	 * @param followIndex The followee data.
	 * @param globalPrefs The preferences.
	 * @param favouritesCache The favourites data.
	 * @param explicitCache The explicit cache.
	 * @return A read-only view of the storage.
	 */
	public static IReadOnlyLocalData openReadOnly(UnlockRead readLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs, FavouritesCacheData favouritesCache, ExplicitCacheData explicitCache)
	{
		return new LoadedStorage(readLock, null, localIndex, globalPinCache, followIndex, globalPrefs, favouritesCache, explicitCache);
	}

	/**
	 * Returns storage as an exclusive, read-write instance.
	 * 
	 * @param writeLock The write lock to release when closing.
	 * @param localIndex The local index.
	 * @param globalPinCache The global pin cache.
	 * @param followIndex The followee data.
	 * @param globalPrefs The preferences.
	 * @param favouritesCache The favourites data.
	 * @param explicitCache The explicit cache.
	 * @return A read-write view of the storage.
	 */
	public static IReadWriteLocalData openReadWrite(UnlockWrite writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs, FavouritesCacheData favouritesCache, ExplicitCacheData explicitCache)
	{
		return new LoadedStorage(null, writeLock, localIndex, globalPinCache, followIndex, globalPrefs, favouritesCache, explicitCache);
	}


	private final UnlockRead _readLock;
	private final UnlockWrite _writeLock;
	private boolean _isOpen;
	private ChannelData _localIndex;
	private boolean _changed_localIndex;
	private PinCacheData _globalPinCache;
	private FolloweeData _followIndex;
	private boolean _changed_followIndex;
	private PrefsData _globalPrefs;
	private boolean _changed_globalPrefs;
	private FavouritesCacheData _favouritesCache;
	private boolean _changed_favouritesCache;
	private ExplicitCacheData _explicitCache;
	private boolean _changed_explicitCache;

	private LoadedStorage(UnlockRead readLock, UnlockWrite writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs, FavouritesCacheData favouritesCache, ExplicitCacheData explicitCache)
	{
		_readLock = readLock;
		_writeLock = writeLock;
		_isOpen = true;
		
		_localIndex = localIndex;
		_globalPinCache = globalPinCache;
		_followIndex = followIndex;
		_globalPrefs = globalPrefs;
		_favouritesCache = favouritesCache;
		_explicitCache = explicitCache;
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
	public FavouritesCacheData readFavouritesCache()
	{
		Assert.assertTrue(_isOpen);
		return _favouritesCache;
	}

	@Override
	public ExplicitCacheData readExplicitCache()
	{
		Assert.assertTrue(_isOpen);
		return _explicitCache;
	}

	@Override
	public void close()
	{
		Assert.assertTrue(_isOpen);
		if (null != _writeLock)
		{
			_writeLock.closeWrite((_changed_localIndex ? _localIndex : null)
					, (_changed_followIndex ? _followIndex : null)
					, (_changed_globalPrefs ? _globalPrefs : null)
					, (_changed_favouritesCache ? _favouritesCache : null)
					, (_changed_explicitCache ? _explicitCache : null)
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
		Assert.assertTrue(_globalPinCache == pinCache);
	}

	@Override
	public void writeFollowIndex(FolloweeData followIndex)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_followIndex = followIndex;
		_changed_followIndex = true;
	}

	@Override
	public void writeExplicitCache(ExplicitCacheData explicitCache)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		Assert.assertTrue(_explicitCache == explicitCache);
		_changed_explicitCache = true;
	}

	@Override
	public void writeFavouritesCache(FavouritesCacheData favouritesCache)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		Assert.assertTrue(_favouritesCache == favouritesCache);
		_changed_favouritesCache = true;
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
		 * NOTE:  Any non-null values must be the same instances returned by a previous read.
		 * 
		 * @param updateLocalIndex Non-null if this should be saved as the new ChannelData.
		 * @param updateFollowIndex Non-null if this should be saved as the new FolloweeData.
		 * @param updateGlobalPrefs Non-null if this should be saved as the new PrefsData.
		 * @param favouritesCache Non-null if this should be saved as the new FavouritesCacheData.
		 * @param updatedExplicitCache Non-null if this should be saved as the new ExplicitCacheData.
		 */
		void closeWrite(ChannelData updateLocalIndex, FolloweeData updateFollowIndex, PrefsData updateGlobalPrefs, FavouritesCacheData favouritesCache, ExplicitCacheData updatedExplicitCache);
	}
}
