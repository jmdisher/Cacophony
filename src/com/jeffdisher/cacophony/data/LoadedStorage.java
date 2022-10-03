package com.jeffdisher.cacophony.data;

import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Implements the read and write access to the local storage.
 * While this is basically just a container which knows how to write-back its changes on close, it also does ensure that
 * there is no access attempts made after it is closed.
 */
public class LoadedStorage implements IReadWriteLocalData
{
	public static IReadOnlyLocalData openReadOnly(LocalDataModel dataModel, LocalDataModel.ReadLock readLock, LocalIndex localIndex, GlobalPinCache globalPinCache, FollowIndex followIndex, GlobalPrefs globalPrefs)
	{
		return new LoadedStorage(dataModel, readLock, null, localIndex, globalPinCache, followIndex, globalPrefs);
	}

	public static IReadWriteLocalData openReadWrite(LocalDataModel dataModel, LocalDataModel.WriteLock writeLock, LocalIndex localIndex, GlobalPinCache globalPinCache, FollowIndex followIndex, GlobalPrefs globalPrefs)
	{
		return new LoadedStorage(dataModel, null, writeLock, localIndex, globalPinCache, followIndex, globalPrefs);
	}


	private final LocalDataModel _dataModel;
	private final LocalDataModel.ReadLock _readLock;
	private final LocalDataModel.WriteLock _writeLock;
	private boolean _isOpen;
	private LocalIndex _localIndex;
	private boolean _changed_localIndex;
	private GlobalPinCache _globalPinCache;
	private boolean _changed_globalPinCache;
	private FollowIndex _followIndex;
	private boolean _changed_followIndex;
	private GlobalPrefs _globalPrefs;
	private boolean _changed_globalPrefs;

	private LoadedStorage(LocalDataModel dataModel, LocalDataModel.ReadLock readLock, LocalDataModel.WriteLock writeLock, LocalIndex localIndex, GlobalPinCache globalPinCache, FollowIndex followIndex, GlobalPrefs globalPrefs)
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
	public LocalIndex readLocalIndex()
	{
		Assert.assertTrue(_isOpen);
		return _localIndex;
	}

	@Override
	public GlobalPrefs readGlobalPrefs()
	{
		Assert.assertTrue(_isOpen);
		return _globalPrefs;
	}

	@Override
	public GlobalPinCache readGlobalPinCache()
	{
		Assert.assertTrue(_isOpen);
		return (null != _globalPinCache)
				? _globalPinCache.mutableClone()
				: null
		;
	}

	@Override
	public FollowIndex readFollowIndex()
	{
		Assert.assertTrue(_isOpen);
		return (null != _followIndex)
				? _followIndex.mutableClone()
				: null
		;
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
	public void writeLocalIndex(LocalIndex index)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_localIndex = index;
		_changed_localIndex = true;
	}

	@Override
	public void writeGlobalPrefs(GlobalPrefs prefs)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_globalPrefs = prefs;
		_changed_globalPrefs = true;
	}

	@Override
	public void writeGlobalPinCache(GlobalPinCache pinCache)
	{
		Assert.assertTrue(_isOpen);
		Assert.assertTrue(null != _writeLock);
		_globalPinCache = pinCache;
		_changed_globalPinCache = true;
	}

	@Override
	public void writeFollowIndex(FollowIndex followIndex)
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
