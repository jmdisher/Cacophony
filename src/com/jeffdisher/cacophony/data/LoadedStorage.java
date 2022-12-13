package com.jeffdisher.cacophony.data;

import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
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
	public static IReadOnlyLocalData openReadOnly(LocalDataModel dataModel, LocalDataModel.ReadLock readLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		return new LoadedStorage(dataModel, readLock, null, localIndex, globalPinCache, followIndex, globalPrefs);
	}

	public static IReadWriteLocalData openReadWrite(LocalDataModel dataModel, LocalDataModel.WriteLock writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		return new LoadedStorage(dataModel, null, writeLock, localIndex, globalPinCache, followIndex, globalPrefs);
	}


	private final LocalDataModel _dataModel;
	private final LocalDataModel.ReadLock _readLock;
	private final LocalDataModel.WriteLock _writeLock;
	private boolean _isOpen;
	private ChannelData _localIndex;
	private boolean _changed_localIndex;
	private PinCacheData _globalPinCache;
	private boolean _changed_globalPinCache;
	private FolloweeData _followIndex;
	private boolean _changed_followIndex;
	private PrefsData _globalPrefs;
	private boolean _changed_globalPrefs;

	private LoadedStorage(LocalDataModel dataModel, LocalDataModel.ReadLock readLock, LocalDataModel.WriteLock writeLock, ChannelData localIndex, PinCacheData globalPinCache, FolloweeData followIndex, PrefsData globalPrefs)
	{
		_dataModel = dataModel;
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
			_dataModel.closeWrite(_writeLock
					, (_changed_localIndex ? _localIndex : null)
					, (_changed_globalPinCache ? _globalPinCache : null)
					, (_changed_followIndex ? _followIndex : null)
					, (_changed_globalPrefs ? _globalPrefs : null)
			);
		}
		else
		{
			_dataModel.closeRead(_readLock);
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

	@Override
	public LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator)
	{
		return _dataModel.lazilyLoadFolloweeCache(cacheGenerator);
	}
}
