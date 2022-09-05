package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StringHelpers;


public class CommandHelpers
{
	/**
	 * A helper for the core logic to refresh a followee.
	 * 
	 * @param environment The environment of the execution.
	 * @param local The local config.
	 * @param publicKey The public key of the followee to refresh.
	 * @throws CacophonyException There was some problem connecting to the IPFS node or the data discovered was malformed.
	 */
	public static void refreshFollowee(IEnvironment environment, LocalConfig local, IpfsKey publicKey) throws CacophonyException
	{
		Assert.assertTrue(null != publicKey);
		
		IOperationLog log = environment.logOperation("Refreshing followee " + publicKey + "...");
		IReadWriteLocalData data = local.getSharedLocalData().openForWrite();
		FollowIndex followIndex = data.readFollowIndex();
		LocalIndex localIndex = data.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = data.readGlobalPinCache();
		GlobalPrefs globalPrefs = data.readGlobalPrefs();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = scheduler.resolvePublicKey(publicKey).get();
		// (we only want to update the last successful root if we can resolve it).
		IpfsFile lastSuccessfulRoot = lastRoot;
		
		try
		{
			// We only try to read the index if it was there (it is very likely that the IPNS key has been purged if the user hasn't refreshed in the last 24 hours).
			if (null != indexRoot)
			{
				_safeReadIndex(environment, local, followIndex, globalPrefs, publicKey, cache, scheduler, checker, lastRoot, indexRoot);
				// We will save this as the new root.
				lastSuccessfulRoot = indexRoot;
				log.finish("Refresh success!");
			}
			else
			{
				log.finish("Refresh skipped since key missing (probably not refreshed): " + publicKey);
				// We don't advance the root in this case.
			}
		}
		finally
		{
			// Even if we threw an exception or skipped the update, we still want to update the followee index and write-back the config.
			followIndex.updateFollowee(publicKey, lastSuccessfulRoot, System.currentTimeMillis());
			// TODO:  Make sure that nothing else in the state is left broken.
			data.writeFollowIndex(followIndex);
			data.writeGlobalPinCache(pinCache);
			data.close();
		}
	}

	/**
	 * The common idiom of serializing, saving to IPFS, and republishing the index to IPNS.
	 * Failures in saving will throw an exception while a failure in publication is merely logged (as this is
	 * considered a degradation, but not a failure).
	 * 
	 * @param remote The remote helpers.
	 * @param streamIndex The index to use as the updated root of the data structure.
	 * @return The hash of the saved index file.
	 * @throws IpfsConnectionException An error occurred while saving the file to IPFS.
	 */
	public static IpfsFile serializeSaveAndPublishIndex(IEnvironment environment, INetworkScheduler scheduler, StreamIndex streamIndex) throws IpfsConnectionException
	{
		// Serialize the index file and save it to the IPFS node.
		IpfsFile hashIndex = scheduler.saveStream(new ByteArrayInputStream(GlobalData.serializeIndex(streamIndex)), true).get();
		// This never returns null.
		Assert.assertTrue(null != hashIndex);
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		StandardEnvironment.IOperationLog log = environment.logOperation("Publishing " + hashIndex);
		// Publish it to IPNS (returns error on failure).
		IpfsConnectionException error = scheduler.publishIndex(hashIndex).get();
		if (null == error)
		{
			log.finish("Success!");
		}
		else
		{
			log.finish("Failed: " + error.getLocalizedMessage());
			environment.logError("WARNING:  Failed to publish new entry to IPNS (the post succeeded, but a republish will be required): " + hashIndex);
		}
		return hashIndex;
	}

	public static void queueAndProcessElementRecordSize(INetworkScheduler scheduler, List<RawElementData> workingRecordList) throws IpfsConnectionException, SizeConstraintException
	{
		_queueAndProcessElementRecordSize(scheduler, workingRecordList);
	}

	public static void pinAndLoadElementRecords(INetworkScheduler scheduler, HighLevelCache cache, IpfsKey publicKey, List<RawElementData> recordList) throws IpfsConnectionException
	{
		_pinAndLoadElementRecords(scheduler, cache, publicKey, recordList);
	}

	public static List<CacheAlgorithm.Candidate<RawElementData>> fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException
	{
		return _fetchLeafSizes(scheduler, videoEdgePixelMax, workingRecordList);
	}

	public static void pinLeaves(HighLevelCache cache, IpfsKey publicKey, RawElementData data)
	{
		_pinLeaves(cache, publicKey, data);
	}

	public static void processLeaves(IEnvironment environment, INetworkScheduler scheduler, FollowIndex followIndex, IpfsKey publicKey, IpfsFile fetchedRoot, long currentTimeMillis, RawElementData data) throws IpfsConnectionException
	{
		_processLeaves(environment, scheduler, followIndex, publicKey, fetchedRoot, currentTimeMillis, data);
	}


	private static void _updateCachedRecords(IEnvironment environment, INetworkScheduler scheduler, HighLevelCache cache, FollowIndex followIndex, IpfsFile fetchedRoot, StreamRecords oldRecords, StreamRecords newRecords, GlobalPrefs prefs, IpfsKey publicKey) throws IpfsConnectionException, SizeConstraintException
	{
		long currentTimeMillis = System.currentTimeMillis();
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		Set<String> removeCids = new HashSet<String>();
		List<RawElementData> additiveRecordList = new ArrayList<>();
		removeCids.addAll(oldRecords.getRecord());
		for (String cid : newRecords.getRecord())
		{
			// Remove this from the set of CIDs to remove.
			boolean didRemove = removeCids.remove(cid);
			if (!didRemove)
			{
				// If we didn't remove it, that means it is something new.
				IpfsFile file = IpfsFile.fromIpfsCid(cid);
				RawElementData data = new RawElementData();
				data.elementRawCid = cid;
				data.elementCid = file;
				additiveRecordList.add(data);
			}
		}
		// Remove the CIDs which are no longer present and consult the follower cache to remove any associated leaf elements.
		Map<IpfsFile, FollowingCacheElement> cacheByElementCid = CacheHelpers.createCachedMap(followIndex.getFollowerRecord(publicKey));
		for (String rawCid : removeCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// If this element was cached, remove it.
			FollowingCacheElement inCache = cacheByElementCid.get(cid);
			if (null != inCache)
			{
				if (null != inCache.imageHash())
				{
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.FILE, inCache.imageHash()).get();
				}
				if (null != inCache.elementHash())
				{
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.FILE, inCache.elementHash()).get();
				}
			}
			cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, cid).get();
		}
		
		// Queue up the async size checks and make sure that they are ok before we proceed.
		_queueAndProcessElementRecordSize(scheduler, additiveRecordList);
		
		// Fetch the StreamRecord instances so we can check the sizes of the elements inside.
		_pinAndLoadElementRecords(scheduler, cache, publicKey, additiveRecordList);
		
		// Now, cache all the element meta-data entries and find their sizes for consideration into the cache.
		int videoEdgePixelMax = prefs.videoEdgePixelMax();
		List<CacheAlgorithm.Candidate<RawElementData>> candidatesList = _fetchLeafSizes(scheduler, videoEdgePixelMax, additiveRecordList);
		
		// Get the total size of all candidates.
		long bytesToAdd = candidatesList.stream().mapToLong((CacheAlgorithm.Candidate<RawElementData> candidate) -> candidate.byteSize()).sum();
		
		long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		CacheHelpers.pruneCacheIfNeeded(cache, followIndex, new CacheAlgorithm(prefs.followCacheTargetBytes(), currentCacheSizeBytes), publicKey, bytesToAdd);
		
		// Since this path will fetch all the new elements (for now, at least), just pin all the leaves.
		candidatesList.forEach((CacheAlgorithm.Candidate<RawElementData> candidate) -> CommandHelpers.pinLeaves(cache, publicKey, candidate.data()));
		
		// Now that all the requests are in-flight, we can start accounting for them as they arrive.
		for (CacheAlgorithm.Candidate<RawElementData> candidate : candidatesList)
		{
			CommandHelpers.processLeaves(environment, scheduler, followIndex, publicKey, fetchedRoot, currentTimeMillis, candidate.data());
		}
	}

	private static void _safeReadIndex(IEnvironment environment, LocalConfig local, FollowIndex followIndex, GlobalPrefs globalPrefs, IpfsKey publicKey, HighLevelCache cache, INetworkScheduler scheduler, LoadChecker checker, IpfsFile lastRoot, IpfsFile indexRoot) throws IpfsConnectionException, SizeConstraintException
	{
		// See if anything changed.
		if (lastRoot.equals(indexRoot))
		{
			environment.logToConsole("Follow index unchanged (" + lastRoot + ")");
		}
		else
		{
			// TODO: Re-orient this code to make additive operations first so that the unpin operations are allowed to fail.
			environment.logToConsole("Follow index changed (" + lastRoot + ") -> (" + indexRoot + ")");
			
			// Verify that this isn't too big.
			long indexSize = scheduler.getSizeInBytes(indexRoot).get();
			if (indexSize > SizeLimits.MAX_INDEX_SIZE_BYTES)
			{
				throw new SizeConstraintException("index", indexSize, SizeLimits.MAX_INDEX_SIZE_BYTES);
			}
			
			// Cache the new root and remove the old one.
			cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, indexRoot).get();
			StreamIndex oldIndex = checker.loadCached(lastRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamIndex newIndex = checker.loadCached(indexRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			Assert.assertTrue(1 == oldIndex.getVersion());
			Assert.assertTrue(1 == newIndex.getVersion());
			if (!oldIndex.getDescription().equals(newIndex.getDescription()))
			{
				IpfsFile oldDescriptionCid = IpfsFile.fromIpfsCid(oldIndex.getDescription());
				IpfsFile newDescriptionCid = IpfsFile.fromIpfsCid(newIndex.getDescription());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newDescriptionCid).get();
				StreamDescription oldDescription = checker.loadCached(oldDescriptionCid, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
				StreamDescription newDescription = checker.loadCached(newDescriptionCid, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
				if (!oldDescription.getPicture().equals(newDescription.getPicture()))
				{
					cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(newDescription.getPicture())).get();
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(oldDescription.getPicture())).get();
				}
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldDescriptionCid).get();
			}
			if (!oldIndex.getRecommendations().equals(newIndex.getRecommendations()))
			{
				IpfsFile oldRecommendationsCid = IpfsFile.fromIpfsCid(oldIndex.getRecommendations());
				IpfsFile newRecommendationsCid = IpfsFile.fromIpfsCid(newIndex.getRecommendations());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newRecommendationsCid).get();
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldRecommendationsCid).get();
			}
			if (!oldIndex.getRecords().equals(newIndex.getRecords()))
			{
				IpfsFile oldRecordsCid = IpfsFile.fromIpfsCid(oldIndex.getRecords());
				IpfsFile newRecordsCid = IpfsFile.fromIpfsCid(newIndex.getRecords());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newRecordsCid).get();
				StreamRecords oldRecords = checker.loadCached(oldRecordsCid, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
				StreamRecords newRecords = checker.loadCached(newRecordsCid, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
				_updateCachedRecords(environment, scheduler, cache, followIndex, lastRoot, oldRecords, newRecords, globalPrefs, publicKey);
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldRecordsCid).get();
			}
			cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, lastRoot).get();
		}
	}

	private static void _queueAndProcessElementRecordSize(INetworkScheduler scheduler, List<RawElementData> workingRecordList) throws IpfsConnectionException, SizeConstraintException
	{
		// Queue up the async size checks and make sure that they are ok before we proceed.
		workingRecordList.forEach((RawElementData data) -> {
			data.futureSize = scheduler.getSizeInBytes(data.elementCid);
		});
		for (RawElementData data : workingRecordList)
		{
			long elementSize = data.futureSize.get();
			// Verify that this isn't too big.
			if (elementSize > SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				throw new SizeConstraintException("record", elementSize, SizeLimits.MAX_RECORD_SIZE_BYTES);
			}
			data.size = elementSize;
			data.futureSize = null;
		}
	}

	private static void _pinAndLoadElementRecords(INetworkScheduler scheduler, HighLevelCache cache, IpfsKey publicKey, List<RawElementData> recordList) throws IpfsConnectionException
	{
		// We first pin the element.
		recordList.forEach((RawElementData data) -> {
			data.futureElementPin = cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, data.elementCid);
		});
		// Then, we load the element.
		for (RawElementData data : recordList)
		{
			data.futureElementPin.get();
			data.futureElementPin = null;
			data.futureRecord = scheduler.readData(data.elementCid, (byte[] raw) -> GlobalData.deserializeRecord(raw));
		}
	}

	private static List<CacheAlgorithm.Candidate<RawElementData>> _fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException
	{
		for (RawElementData data : workingRecordList)
		{
			data.record = data.futureRecord.get();
			data.futureRecord = null;
			CacheHelpers.chooseAndFetchLeafSizes(scheduler, videoEdgePixelMax, data);
		}
		// Resolve all sizes;
		List<CacheAlgorithm.Candidate<RawElementData>> candidatesList = new ArrayList<>();
		for (RawElementData data : workingRecordList)
		{
			// We will drop entries if we fail to look them up.
			boolean isVerified = false;
			long bytesForLeaves = 0L;
			try
			{
				// Now, find the size of the relevant leaves within.
				if (null != data.thumbnailSizeFuture)
				{
					data.thumbnailSize = data.thumbnailSizeFuture.get();
					data.thumbnailSizeFuture = null;
					bytesForLeaves += data.thumbnailSize;
				}
				if (null != data.videoSizeFuture)
				{
					data.videoSize = data.videoSizeFuture.get();
					data.videoSizeFuture = null;
					bytesForLeaves += data.videoSize;
				}
				isVerified = true;
			}
			catch (IpfsConnectionException e)
			{
				// We failed to load some of the leaves so we will skip this.
				isVerified = false;
			}
			
			if (isVerified)
			{
				// We didn't hit an exception so we will add it to the set.
				// Note that the candidates are considered with weight on the earlier elements in this list so we want to make sure the more recent ones appear there.
				candidatesList.add(0, new CacheAlgorithm.Candidate<RawElementData>(bytesForLeaves, data));
			}
		}
		return candidatesList;
	}

	private static void _pinLeaves(HighLevelCache cache, IpfsKey publicKey, RawElementData data)
	{
		data.futureThumbnailPin = (null != data.thumbnailHash)
				? cache.addToFollowCache(publicKey, HighLevelCache.Type.FILE, data.thumbnailHash)
				: null
		;
		data.futureVideoPin = (null != data.videoHash)
				? cache.addToFollowCache(publicKey, HighLevelCache.Type.FILE, data.videoHash)
				: null
		;
	}

	private static void _processLeaves(IEnvironment environment, INetworkScheduler scheduler, FollowIndex followIndex, IpfsKey publicKey, IpfsFile fetchedRoot, long currentTimeMillis, RawElementData data) throws IpfsConnectionException
	{
		environment.logToConsole("Caching entry: " + data.elementRawCid);
		// Make sure that we have pinned the elements before we proceed.
		if (null != data.futureThumbnailPin)
		{
			data.futureThumbnailPin.get();
			data.futureThumbnailPin = null;
		}
		if (null != data.futureVideoPin)
		{
			data.futureVideoPin.get();
			data.futureVideoPin = null;
		}
		long leafBytes = CacheHelpers.addPinnedLeavesToFollowCache(scheduler, followIndex, publicKey, fetchedRoot, currentTimeMillis, data.elementRawCid, data.thumbnailHash, data.videoHash);
		environment.logToConsole("\tleaf elements: " + StringHelpers.humanReadableBytes(leafBytes));
	}
}
