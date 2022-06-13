package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class CommandHelpers
{
	/**
	 * A helper for the core logic to refresh a followee.
	 * 
	 * @param environment The environment of the execution.
	 * @param local The local config.
	 * @param followIndex The follower index.
	 * @param publicKey The public key of the followee to refresh.
	 * @throws CacophonyException There was some problem connecting to the IPFS node or the data discovered was malformed.
	 */
	public static void refreshFollowee(IEnvironment environment, LocalConfig local, FollowIndex followIndex, IpfsKey publicKey) throws CacophonyException
	{
		Assert.assertTrue(null != publicKey);
		
		IOperationLog log = environment.logOperation("Refreshing followee " + publicKey + "...");
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = scheduler.resolvePublicKey(publicKey).get();
		Assert.assertTrue(null != indexRoot);
		
		try
		{
			_safeReadIndex(environment, local, followIndex, publicKey, cache, scheduler, checker, lastRoot, indexRoot);
		}
		finally
		{
			// Even if we threw an exception, we still want to update the followee index and write-back the config.
			followIndex.updateFollowee(publicKey, indexRoot, System.currentTimeMillis());
			// TODO:  Make sure that nothing else in the state is left broken.
			local.writeBackConfig();
		}
		log.finish("Refresh completed!");
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


	private static void _updateCachedRecords(INetworkScheduler scheduler, HighLevelCache cache, FollowIndex followIndex, IpfsFile fetchedRoot, StreamRecords oldRecords, StreamRecords newRecords, GlobalPrefs prefs, IpfsKey publicKey) throws IpfsConnectionException, SizeConstraintException
	{
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		Set<String> removeCids = new HashSet<String>();
		Set<String> addCids = new HashSet<String>();
		removeCids.addAll(oldRecords.getRecord());
		for (String cid : newRecords.getRecord())
		{
			// Remove this from the set of CIDs to remove.
			boolean didRemove = removeCids.remove(cid);
			if (!didRemove)
			{
				// If we didn't remove it, that means it is something new.
				addCids.add(cid);
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
		
		// First, see how much data we want to add and pre-prune our cache.
		// Verify the sizes.
		List<FutureSize> sizes = new ArrayList<>();
		for (String rawCid : addCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			sizes.add(scheduler.getSizeInBytes(cid));
		}
		for (FutureSize size : sizes)
		{
			long elementSize = size.get();
			// Verify that this isn't too big.
			if (elementSize > SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				throw new SizeConstraintException("record", elementSize, SizeLimits.MAX_RECORD_SIZE_BYTES);
			}
		}
		
		// Fetch all the leaf records.
		List<AsyncRecord> asyncRecords = new ArrayList<>();
		for (String rawCid : addCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			
			// Note that we need to add the element before we can dive into it to check the size of the leaves within.
			cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, cid).get();
			FutureRead<StreamRecord> future = scheduler.readData(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
			asyncRecords.add(new AsyncRecord(rawCid, future));
		}
		
		// NOTE:  We currently always add all new elements but we may restrict this in the future to be more like the "start following" case.
		int videoEdgePixelMax = prefs.videoEdgePixelMax();
		long bytesToAdd = 0L;
		Set<String> verifiedCids = new HashSet<>();
		for (AsyncRecord async : asyncRecords)
		{
			String rawCid = async.rawCid;
			
			boolean isVerified = false;
			try
			{
				// Now, find the size of the relevant leaves within.
				bytesToAdd += CacheHelpers.sizeInBytesToAdd(scheduler, videoEdgePixelMax, async.future.get());
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
				verifiedCids.add(rawCid);
			}
		}
		long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		CacheHelpers.pruneCacheIfNeeded(cache, followIndex, new CacheAlgorithm(prefs.followCacheTargetBytes(), currentCacheSizeBytes), publicKey, bytesToAdd);
		
		// Now, populate the cache with the new elements.
		long currentTimeMillis = System.currentTimeMillis();
		List<CacheHelpers.LeafTuple> asyncLeaves = new ArrayList<>();
		for (AsyncRecord async : asyncRecords)
		{
			if (verifiedCids.contains(async.rawCid))
			{
				CacheHelpers.LeafTuple tuple = CacheHelpers.findAndPinLeaves(cache, publicKey, async.rawCid, videoEdgePixelMax, async.future.get());
				asyncLeaves.add(tuple);
			}
		}
		
		// Account for the cache elements we just pinned.
		for (CacheHelpers.LeafTuple leaves : asyncLeaves)
		{
			// Make sure that we have pinned the elements before we proceed.
			if (null != leaves.imagePin())
			{
				leaves.imagePin().get();
			}
			if (null != leaves.leafPin())
			{
				leaves.leafPin().get();
			}
			CacheHelpers.addPinnedLeavesToFollowCache(scheduler, followIndex, publicKey, fetchedRoot, currentTimeMillis, leaves.elementRawCid(), leaves.imageHash(), leaves.leafHash());
			
		}
	}

	private static void _safeReadIndex(IEnvironment environment, LocalConfig local, FollowIndex followIndex, IpfsKey publicKey, HighLevelCache cache, INetworkScheduler scheduler, LoadChecker checker, IpfsFile lastRoot, IpfsFile indexRoot) throws IpfsConnectionException, SizeConstraintException
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
				_updateCachedRecords(scheduler, cache, followIndex, lastRoot, oldRecords, newRecords, local.readSharedPrefs(), publicKey);
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldRecordsCid).get();
			}
			cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, lastRoot).get();
		}
	}


	private static record AsyncRecord(String rawCid, FutureRead<StreamRecord> future)
	{
	}
}
