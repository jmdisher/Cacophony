package com.jeffdisher.cacophony.logic;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
		IConnection sharedConnection = local.getSharedConnection();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, sharedConnection, localIndex.keyName());
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		LoadChecker checker = new LoadChecker(remote, pinCache, sharedConnection);
		HighLevelCache cache = new HighLevelCache(pinCache, sharedConnection);
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = remote.resolvePublicKey(publicKey);
		Assert.assertTrue(null != indexRoot);
		
		// See if anything changed.
		if (lastRoot.equals(indexRoot))
		{
			environment.logToConsole("Follow index unchanged (" + lastRoot + ")");
		}
		else
		{
			environment.logToConsole("Follow index changed (" + lastRoot + ") -> (" + indexRoot + ")");
			
			// Verify that this isn't too big.
			long indexSize = remote.getSizeInBytes(indexRoot);
			if (indexSize > SizeLimits.MAX_INDEX_SIZE_BYTES)
			{
				throw new SizeConstraintException("index", indexSize, SizeLimits.MAX_INDEX_SIZE_BYTES);
			}
			
			// Cache the new root and remove the old one.
			cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, indexRoot);
			StreamIndex oldIndex = GlobalData.deserializeIndex(checker.loadCached(lastRoot));
			StreamIndex newIndex = GlobalData.deserializeIndex(checker.loadCached(indexRoot));
			Assert.assertTrue(1 == oldIndex.getVersion());
			Assert.assertTrue(1 == newIndex.getVersion());
			if (!oldIndex.getDescription().equals(newIndex.getDescription()))
			{
				IpfsFile oldDescriptionCid = IpfsFile.fromIpfsCid(oldIndex.getDescription());
				IpfsFile newDescriptionCid = IpfsFile.fromIpfsCid(newIndex.getDescription());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newDescriptionCid);
				StreamDescription oldDescription = GlobalData.deserializeDescription(checker.loadCached(oldDescriptionCid));
				StreamDescription newDescription = GlobalData.deserializeDescription(checker.loadCached(newDescriptionCid));
				if (!oldDescription.getPicture().equals(newDescription.getPicture()))
				{
					cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(newDescription.getPicture()));
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, IpfsFile.fromIpfsCid(oldDescription.getPicture()));
				}
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldDescriptionCid);
			}
			if (!oldIndex.getRecommendations().equals(newIndex.getRecommendations()))
			{
				IpfsFile oldRecommendationsCid = IpfsFile.fromIpfsCid(oldIndex.getRecommendations());
				IpfsFile newRecommendationsCid = IpfsFile.fromIpfsCid(newIndex.getRecommendations());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newRecommendationsCid);
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldRecommendationsCid);
			}
			if (!oldIndex.getRecords().equals(newIndex.getRecords()))
			{
				IpfsFile oldRecordsCid = IpfsFile.fromIpfsCid(oldIndex.getRecords());
				IpfsFile newRecordsCid = IpfsFile.fromIpfsCid(newIndex.getRecords());
				cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, newRecordsCid);
				StreamRecords oldRecords = GlobalData.deserializeRecords(checker.loadCached(oldRecordsCid));
				StreamRecords newRecords = GlobalData.deserializeRecords(checker.loadCached(newRecordsCid));
				_updateCachedRecords(remote, cache, followIndex, lastRoot, oldRecords, newRecords, local.readSharedPrefs(), publicKey);
				cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, oldRecordsCid);
			}
			cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, lastRoot);
			// Update the root in our cache.
			followIndex.updateFollowee(publicKey, indexRoot, System.currentTimeMillis());
		}
		local.writeBackConfig();
		log.finish("Refresh completed!");
	}


	private static void _updateCachedRecords(RemoteActions remote, HighLevelCache cache, FollowIndex followIndex, IpfsFile fetchedRoot, StreamRecords oldRecords, StreamRecords newRecords, GlobalPrefs prefs, IpfsKey publicKey) throws IpfsConnectionException, SizeConstraintException
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
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.FILE, inCache.imageHash());
				}
				if (null != inCache.elementHash())
				{
					cache.removeFromFollowCache(publicKey, HighLevelCache.Type.FILE, inCache.elementHash());
				}
			}
			cache.removeFromFollowCache(publicKey, HighLevelCache.Type.METADATA, cid);
		}
		
		// First, see how much data we want to add and pre-prune our cache.
		// NOTE:  We currently always add all new elements but we may restrict this in the future to be more like the "start following" case.
		int videoEdgePixelMax = prefs.videoEdgePixelMax();
		long bytesToAdd = 0L;
		for (String rawCid : addCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// Verify that this isn't too big.
			long elementSize = remote.getSizeInBytes(cid);
			if (elementSize > SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				throw new SizeConstraintException("record", elementSize, SizeLimits.MAX_RECORD_SIZE_BYTES);
			}
			
			// Note that we need to add the element before we can dive into it to check the size of the leaves within.
			cache.addToFollowCache(publicKey, HighLevelCache.Type.METADATA, cid);
			// Now, find the size of the relevant leaves within.
			bytesToAdd += CacheHelpers.sizeInBytesToAdd(remote, videoEdgePixelMax, rawCid);
		}
		long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		CacheHelpers.pruneCacheIfNeeded(cache, followIndex, new CacheAlgorithm(prefs.followCacheTargetBytes(), currentCacheSizeBytes), publicKey, bytesToAdd);
		
		// Now, populate the cache with the new elements.
		long currentTimeMillis = System.currentTimeMillis();
		for (String rawCid : addCids)
		{
			CacheHelpers.addElementToCache(remote, cache, followIndex, publicKey, fetchedRoot, videoEdgePixelMax, currentTimeMillis, rawCid);
		}
	}
}