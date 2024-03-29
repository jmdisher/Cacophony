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
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.ILogger;
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
	private final List<IpfsFile> _initialFailuresToRetry;
	private final Set<IpfsFile> _initialFailureSet;

	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;
	private final List<Consumer<CacheUpdater>> _pendingCacheUpdates;

	private final List<IpfsFile> _elementsRetried;
	private final List<IpfsFile> _temporarilySkippedRecords;
	private final List<IpfsFile> _permanentlySkippedRecords;

	/**
	 * Creates the refresh support instance.
	 * 
	 * @param logger The logger.
	 * @param transaction The transaction all actions will use (since this could be long-running).
	 * @param followeeKey The public key of the followee to refresh.
	 * @param isExistingFollowee True if this is someone we are already following (false if they are new).
	 * @param cachedEntriesForFollowee The map of entries we have cached something about, for this user.
	 * @param initialFailureToRetry The initial list of elements we previously failed temporarily and may retry.
	 * @param initialFailureSet The initial set of elements we have previously failed to load (both permanent and temporary).
	 */
	public StandardRefreshSupport(ILogger logger
			, ConcurrentTransaction transaction
			, IpfsKey followeeKey
			, boolean isExistingFollowee
			, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee
			, List<IpfsFile> initialFailuresToRetry
			, Set<IpfsFile> initialFailureSet
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
		_initialFailuresToRetry = initialFailuresToRetry;
		_initialFailureSet = initialFailureSet;
		
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
		_pendingCacheUpdates = new ArrayList<>();
		
		_elementsRetried = new ArrayList<>();
		_temporarilySkippedRecords = new ArrayList<>();
		_permanentlySkippedRecords = new ArrayList<>();
	}

	/**
	 * Commits all changes to the followee to the given followees data.
	 * 
	 * @param followees The data to modify.
	 */
	public void commitFolloweeChanges(FolloweeData followees)
	{
		// Update the state of any records we decided to retry, removing them from the skipped state (although they may be re-added, below).
		for (IpfsFile retried : _elementsRetried)
		{
			followees.removeTemporarilySkippedRecord(_followeeKey, retried);
		}
		
		// Handle anything removed.
		for (IpfsFile cid : _elementsToRemoveFromCache)
		{
			followees.removeElement(_followeeKey, cid);
		}
		// Handle anything added.
		for (FollowingCacheElement elt : _elementsToAddToCache)
		{
			followees.addElement(_followeeKey, elt);
		}
		
		// Write-back the failure data.
		for (IpfsFile failure : _permanentlySkippedRecords)
		{
			followees.addSkippedRecord(_followeeKey, failure, true);
		}
		for (IpfsFile failure : _temporarilySkippedRecords)
		{
			followees.addSkippedRecord(_followeeKey, failure, false);
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
	public void logMessageImportant(String message)
	{
		_logger.logOperation(message);
	}
	@Override
	public void logMessageVerbose(String message)
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
	public FollowingCacheElement getCacheDataForElement(IpfsFile elementHash)
	{
		return _cachedEntriesForFollowee.get(elementHash);
	}
	@Override
	public void addRecordForFollowee(IpfsFile elementHash)
	{
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.newFolloweePostObserved(_followeeKey, elementHash, 0L);
		});
	}
	@Override
	public void cacheRecordForFollowee(IpfsFile elementHash, AbstractRecord recordData, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes)
	{
		IpfsFile leafHash = (null != audioLeaf) ? audioLeaf : videoLeaf;
		_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.cachedFolloweePost(_followeeKey, elementHash, recordData, imageHash, audioLeaf, videoLeaf, videoEdgeSize);
		});
	}
	@Override
	public void removeRecordForFollowee(IpfsFile elementHash)
	{
		_pendingCacheUpdates.add((CacheUpdater cacheUpdater) -> {
			cacheUpdater.existingFolloweePostDisappeared(_followeeKey, elementHash);
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
	@Override
	public void addSkippedRecord(IpfsFile recordCid, boolean isPermanent)
	{
		// These shouldn't already be in any collections (duplicates should be avoided in the FolloweeRefreshLogic implementation).
		Assert.assertTrue(!_cachedEntriesForFollowee.containsKey(recordCid));
		Assert.assertTrue(!_permanentlySkippedRecords.contains(recordCid));
		Assert.assertTrue(!_temporarilySkippedRecords.contains(recordCid));
		if (isPermanent)
		{
			_permanentlySkippedRecords.add(recordCid);
		}
		else
		{
			_temporarilySkippedRecords.add(recordCid);
		}
	}
	@Override
	public boolean hasRecordBeenProcessed(IpfsFile recordCid)
	{
		// We only check the initial state - the caller is responsible for tracking anything it has, or is currently
		// processing, in this invocation.
		return _cachedEntriesForFollowee.containsKey(recordCid)
				|| _initialFailureSet.contains(recordCid)
		;
	}
	@Override
	public IpfsFile getAndResetNextInitialTemporarySkip()
	{
		IpfsFile toRetry = null;
		if (!_initialFailuresToRetry.isEmpty())
		{
			toRetry = _initialFailuresToRetry.remove(0);
			_elementsRetried.add(toRetry);
		}
		return toRetry;
	}
}
