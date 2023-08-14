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
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
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
	private final Map<IpfsFile, FollowingCacheElement> _cachedEntriesForFollowee;
	private final CacheUpdater _cacheUpdater;
	
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;
	private final List<Consumer<CacheUpdater>> _localRecordCacheUpdates;
	private final List<Consumer<CacheUpdater>> _userInfoCacheUpdates;
	private final List<Consumer<CacheUpdater>> _replyCacheUpdates;

	public StandardRefreshSupport(ILogger logger
			, ConcurrentTransaction transaction
			, IpfsKey followeeKey
			, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee
			, CacheUpdater cacheUpdater
	)
	{
		Assert.assertTrue(null != logger);
		Assert.assertTrue(null != transaction);
		Assert.assertTrue(null != followeeKey);
		Assert.assertTrue(null != cachedEntriesForFollowee);
		
		_logger = logger;
		_transaction = transaction;
		_followeeKey = followeeKey;
		_cachedEntriesForFollowee = cachedEntriesForFollowee;
		_cacheUpdater = cacheUpdater;
		
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
		_localRecordCacheUpdates = new ArrayList<>();
		_userInfoCacheUpdates = new ArrayList<>();
		_replyCacheUpdates = new ArrayList<>();
	}

	public void commitFolloweeChanges(IFolloweeWriting followees)
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
		for (Consumer<CacheUpdater> consumer : _localRecordCacheUpdates)
		{
			consumer.accept(cacheUpdater);
		}
		for (Consumer<CacheUpdater> consumer : _userInfoCacheUpdates)
		{
			consumer.accept(cacheUpdater);
		}
		for (Consumer<CacheUpdater> consumer : _replyCacheUpdates)
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
		_userInfoCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.userInfoCache_setFolloweeUserInfo(_followeeKey, description);
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
	public void addElementToCache(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes)
	{
		_cacheUpdater.entryRegistry_addFolloweeElement(_followeeKey, elementHash);
		IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
		_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		IpfsFile replyTo = recordData.getReplyTo();
		_localRecordCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.recordCache_recordMetaDataPinned(elementHash
					, recordData.getName()
					, recordData.getDescription()
					, recordData.getPublishedSecondsUtc()
					, recordData.getDiscussionUrl()
					, recordData.getPublisherKey()
					, replyTo
					, recordData.getExternalElementCount()
			);
			if (null != imageHash)
			{
				cacheUpdater.recordCache_recordThumbnailPinned(elementHash, imageHash);
			}
			if (null != audioLeaf)
			{
				cacheUpdater.recordCache_recordAudioPinned(elementHash, audioLeaf);
			}
			if (null != videoLeaf)
			{
				cacheUpdater.recordCache_recordVideoPinned(elementHash, videoLeaf, videoEdgeSize);
			}
		});
		if (null != replyTo)
		{
			_replyCacheUpdates.add((CacheUpdater cacheUpdater) -> {
				cacheUpdater.replyCache_addFolloweePost(elementHash, replyTo);
			});
		}
	}
	@Override
	public void removeElementFromCache(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioHash, IpfsFile videoHash, int videoEdgeSize)
	{
		_elementsToRemoveFromCache.add(elementHash);
		_cacheUpdater.entryRegistry_removeFolloweeElement(_followeeKey, elementHash);
		_localRecordCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			if (null != imageHash)
			{
				cacheUpdater.recordCache_recordThumbnailReleased(elementHash, imageHash);
			}
			if (null != audioHash)
			{
				cacheUpdater.recordCache_recordAudioReleased(elementHash, audioHash);
			}
			if (null != videoHash)
			{
				cacheUpdater.recordCache_recordVideoReleased(elementHash, videoHash, videoEdgeSize);
			}
			cacheUpdater.recordCache_recordMetaDataReleased(elementHash);
		});
		if (null != recordData.getReplyTo())
		{
			_replyCacheUpdates.add((CacheUpdater cacheUpdater) -> {
				cacheUpdater.replyCache_removeFolloweePost(elementHash);
			});
		}
	}
}
