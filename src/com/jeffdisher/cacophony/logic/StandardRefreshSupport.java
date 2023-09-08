package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final ILogger _logger;
	private final ConcurrentTransaction _transaction;
	private final IpfsKey _followeeKey;
	private final boolean _isExistingFollowee;
	private final Map<IpfsFile, FollowingCacheElement> _cachedEntriesForFollowee;
	
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;
	private final List<Consumer<CacheUpdater>> _pendingCacheUpdates;

	public StandardRefreshSupport(ILogger logger
			, ConcurrentTransaction transaction
			, IpfsKey followeeKey
			, boolean isExistingFollowee
			, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee
	)
	{
		Assert.assertTrue(null != logger);
		Assert.assertTrue(null != transaction);
		Assert.assertTrue(null != followeeKey);
		Assert.assertTrue(null != cachedEntriesForFollowee);
		
		_logger = logger;
		_transaction = transaction;
		_followeeKey = followeeKey;
		_isExistingFollowee = isExistingFollowee;
		_cachedEntriesForFollowee = cachedEntriesForFollowee;
		
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
		_pendingCacheUpdates = new ArrayList<>();
	}

	public void commitFolloweeChanges(FolloweeData followees)
	{
		for (IpfsFile cid : _elementsToRemoveFromCache)
		{
			followees.removeElement(_followeeKey, cid);
		}
		for (FollowingCacheElement elt : _elementsToAddToCache)
		{
			followees.addElement(_followeeKey, elt);
		}
	}

	/**
	 * Called on successful followee refresh to write-back changes to the recordCache.
	 * 
	 * @param cacheUpdater The cache updater.
	 */
	public void commitLocalCacheUpdates(CacheUpdater cacheUpdater)
	{
		for (Consumer<CacheUpdater> consumer : _pendingCacheUpdates)
		{
			consumer.accept(cacheUpdater);
		}
	}

	@Override
	public void logMessage(String message)
	{
		_logger.logVerbose(message);
	}
	@Override
	public void followeeDescriptionNewOrUpdated(AbstractDescription description)
	{
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			// We use a different update mechanism if this is a new or existing followee.
			if (_isExistingFollowee)
			{
				cacheUpdater.updatedFolloweeInfo(_followeeKey, description);
			}
			else
			{
				cacheUpdater.addedFollowee(_followeeKey, description);
			}
		});
	}
	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _transaction.getSizeInBytes(cid);
	}
	@Override
	public FuturePin addMetaDataToFollowCache(IpfsFile cid)
	{
		return _transaction.pin(cid);
	}
	@Override
	public void deferredRemoveMetaDataFromFollowCache(IpfsFile cid)
	{
		_transaction.unpin(cid);
	}
	@Override
	public FuturePin addFileToFollowCache(IpfsFile cid)
	{
		return _transaction.pin(cid);
	}
	@Override
	public void deferredRemoveFileFromFollowCache(IpfsFile cid)
	{
		_transaction.unpin(cid);
	}
	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		return _transaction.loadCached(file, decoder);
	}
	@Override
	public IpfsFile getImageForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _cachedEntriesForFollowee.get(elementHash);
		return (null != elt)
				? elt.imageHash()
				: null
		;
	}
	@Override
	public IpfsFile getLeafForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _cachedEntriesForFollowee.get(elementHash);
		return (null != elt)
				? elt.leafHash()
				: null
		;
	}
	@Override
	public void addRecordForFollowee(IpfsFile elementHash, long publishedSecondsUtc)
	{
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.newFolloweePostObserved(_followeeKey, elementHash, publishedSecondsUtc);
		});
	}
	@Override
	public void cacheRecordForFollowee(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes)
	{
		IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
		// We only want to cache the actual element if there is at least an image or leaf.
		if ((null != imageHash) || (null != leafHash))
		{
			_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		}
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.cachedFolloweePost(_followeeKey, elementHash, recordData, imageHash, audioLeaf, videoLeaf, videoEdgeSize);
		});
	}
	@Override
	public void removeElementFromCache(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioHash, IpfsFile videoHash, int videoEdgeSize)
	{
		_elementsToRemoveFromCache.add(elementHash);
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.removedFolloweePost(_followeeKey, elementHash, recordData, imageHash, audioHash, videoHash, videoEdgeSize);
		});
	}
}
