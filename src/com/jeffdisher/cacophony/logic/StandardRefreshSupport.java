package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
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
	private final IEnvironment _environment;
	private final ConcurrentTransaction _transaction;
	private final IpfsKey _followeeKey;
	private final Map<IpfsFile, FollowingCacheElement> _cachedEntriesForFollowee;
	private final HandoffConnector<IpfsFile, Void> _connectorForUser;
	
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;
	private final List<Consumer<LocalRecordCache>> _localRecordCacheUpdates;

	public StandardRefreshSupport(IEnvironment environment
			, ConcurrentTransaction transaction
			, IpfsKey followeeKey
			, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee
			, HandoffConnector<IpfsFile, Void> connectorForUser
	)
	{
		Assert.assertTrue(null != environment);
		Assert.assertTrue(null != transaction);
		Assert.assertTrue(null != followeeKey);
		Assert.assertTrue(null != cachedEntriesForFollowee);
		// connectorForUser can be null.
		
		_environment = environment;
		_transaction = transaction;
		_followeeKey = followeeKey;
		_cachedEntriesForFollowee = cachedEntriesForFollowee;
		_connectorForUser = connectorForUser;
		
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
		_localRecordCacheUpdates = new ArrayList<>();
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
	 */
	public void commitLocalCacheUpdates(LocalRecordCache recordCache)
	{
		for (Consumer<LocalRecordCache> consumer : _localRecordCacheUpdates)
		{
			consumer.accept(recordCache);
		}
	}

	@Override
	public void logMessage(String message)
	{
		_environment.logToConsole(message);
	}
	@Override
	public void newElementPinned(IpfsFile elementHash, String name, String description, long publishedSecondsUtc, String discussionUrl, String publisherKey, int leafReferenceCount)
	{
		if (null != _connectorForUser)
		{
			// We want to record that we are aware of this, whether or not we are actually going to cache it.
			_connectorForUser.create(elementHash, null);
		}
		_localRecordCacheUpdates.add((LocalRecordCache cache) -> {
			cache.recordMetaDataPinned(elementHash, name, description, publishedSecondsUtc, discussionUrl, publisherKey, leafReferenceCount);
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
	public void addElementToCache(IpfsFile elementHash, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes)
	{
		IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
		_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		_localRecordCacheUpdates.add((LocalRecordCache cache) -> {
			cache.recordThumbnailPinned(elementHash, imageHash);
			if (null != audioLeaf)
			{
				cache.recordAudioPinned(elementHash, audioLeaf);
			}
			if (null != videoLeaf)
			{
				cache.recordVideoPinned(elementHash, videoLeaf, videoEdgeSize);
			}
		});
	}
	@Override
	public void removeElementFromCache(IpfsFile elementHash)
	{
		_elementsToRemoveFromCache.add(elementHash);
		if (null != _connectorForUser)
		{
			// Even if this element wasn't in the cache, we are still saying it was removed.
			_connectorForUser.destroy(elementHash);
		}
		_localRecordCacheUpdates.add((LocalRecordCache cache) -> {
			cache.recordMetaDataReleased(elementHash);
		});
	}
}
