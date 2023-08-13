package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.caches.EntryCacheRegistry;
import com.jeffdisher.cacophony.caches.HomeUserReplyCache;
import com.jeffdisher.cacophony.caches.LocalRecordCache;
import com.jeffdisher.cacophony.caches.LocalUserInfoCache;
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
	private final EntryCacheRegistry _entryRegistry;
	
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;
	private final List<Consumer<LocalRecordCache>> _localRecordCacheUpdates;
	private final List<Consumer<LocalUserInfoCache>> _userInfoCacheUpdates;
	private final List<Consumer<HomeUserReplyCache>> _replyCacheUpdates;

	public StandardRefreshSupport(ILogger logger
			, ConcurrentTransaction transaction
			, IpfsKey followeeKey
			, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee
			, EntryCacheRegistry entryRegistry
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
		_entryRegistry = entryRegistry;
		
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
	 * @param recordCache The cache to update.
	 * @param userInfoCache The user info cache to update.
	 * @param replyCache The replyTo cache to update.
	 */
	public void commitLocalCacheUpdates(LocalRecordCache recordCache, LocalUserInfoCache userInfoCache, HomeUserReplyCache replyCache)
	{
		for (Consumer<LocalRecordCache> consumer : _localRecordCacheUpdates)
		{
			consumer.accept(recordCache);
		}
		for (Consumer<LocalUserInfoCache> consumer : _userInfoCacheUpdates)
		{
			consumer.accept(userInfoCache);
		}
		for (Consumer<HomeUserReplyCache> consumer : _replyCacheUpdates)
		{
			consumer.accept(replyCache);
		}
	}

	@Override
	public void logMessage(String message)
	{
		_logger.logVerbose(message);
	}
	@Override
	public void followeeDescriptionNewOrUpdated(String name, String description, IpfsFile userPicCid, String emailOrNull, String websiteOrNull, IpfsFile featureOrNull)
	{
		_userInfoCacheUpdates.add((LocalUserInfoCache userInfoCache) -> {
			userInfoCache.setUserInfo(_followeeKey, name, description, userPicCid, emailOrNull, websiteOrNull, featureOrNull);
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
		if (null != _entryRegistry)
		{
			// We want to record that we are aware of this, whether or not we are actually going to cache it.
			_entryRegistry.addFolloweeElement(_followeeKey, elementHash);
		}
		IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
		_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		IpfsFile replyTo = recordData.getReplyTo();
		_localRecordCacheUpdates.add((LocalRecordCache cache) -> {
			cache.recordMetaDataPinned(elementHash
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
				cache.recordThumbnailPinned(elementHash, imageHash);
			}
			if (null != audioLeaf)
			{
				cache.recordAudioPinned(elementHash, audioLeaf);
			}
			if (null != videoLeaf)
			{
				cache.recordVideoPinned(elementHash, videoLeaf, videoEdgeSize);
			}
		});
		if (null != replyTo)
		{
			_replyCacheUpdates.add((HomeUserReplyCache replyCache) -> {
				replyCache.addFolloweePost(elementHash, replyTo);
			});
		}
	}
	@Override
	public void removeElementFromCache(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioHash, IpfsFile videoHash, int videoEdgeSize)
	{
		_elementsToRemoveFromCache.add(elementHash);
		if (null != _entryRegistry)
		{
			// Even if this element wasn't in the cache, we are still saying it was removed.
			_entryRegistry.removeFolloweeElement(_followeeKey, elementHash);
		}
		_localRecordCacheUpdates.add((LocalRecordCache cache) -> {
			if (null != imageHash)
			{
				cache.recordThumbnailReleased(elementHash, imageHash);
			}
			if (null != audioHash)
			{
				cache.recordAudioReleased(elementHash, audioHash);
			}
			if (null != videoHash)
			{
				cache.recordVideoReleased(elementHash, videoHash, videoEdgeSize);
			}
			cache.recordMetaDataReleased(elementHash);
		});
		if (null != recordData.getReplyTo())
		{
			_replyCacheUpdates.add((HomeUserReplyCache replyCache) -> {
				replyCache.removeFolloweePost(elementHash);
			});
		}
	}
}
