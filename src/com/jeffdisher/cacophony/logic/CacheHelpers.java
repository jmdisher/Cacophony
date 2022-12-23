package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.Pair;


/**
 * Miscellaneous helper functions related to follower cache management.
 */
public class CacheHelpers
{
	// We currently want to make sure the cache is at most 90% full before caching a new channel.
	private static final double CACHE_MAX_MULTIPLIER_PRE_NEW_CHANNEL = 0.90;

	public static void chooseAndFetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, RawElementData element) throws IpfsConnectionException
	{
		// We will go through the elements, looking for the special image and the last, largest video element no larger than our resolution limit.
		IpfsFile imageHash = null;
		IpfsFile leafHash = null;
		int biggestEdge = 0;
		for (DataElement elt : element.record.getElements().getElement())
		{
			IpfsFile eltCid = IpfsFile.fromIpfsCid(elt.getCid());
			if (null != elt.getSpecial())
			{
				Assert.assertTrue(null == imageHash);
				imageHash = eltCid;
			}
			else if ((elt.getWidth() >= biggestEdge) && (elt.getWidth() <= videoEdgePixelMax) && (elt.getHeight() >= biggestEdge) && (elt.getHeight() <= videoEdgePixelMax))
			{
				biggestEdge = Math.max(elt.getWidth(), elt.getHeight());
				leafHash = eltCid;
			}
		}
		if (null != imageHash)
		{
			element.thumbnailHash = imageHash;
			element.thumbnailSizeFuture = scheduler.getSizeInBytes(imageHash);
		}
		if (null != leafHash)
		{
			element.leafHash = leafHash;
			element.leafSizeFuture = scheduler.getSizeInBytes(leafHash);
		}
	}

	/**
	 * Returns a reduced cache size we should target to shrink down the stored data before we proceed to cache a new
	 * channel.
	 * 
	 * @param prefs The GlobalPrefs object.
	 * @return The reduced cache size, in bytes, we should target.
	 */
	public static long getTargetCacheSizeBeforeNewChannel(GlobalPrefs prefs)
	{
		return (long) (CACHE_MAX_MULTIPLIER_PRE_NEW_CHANNEL * (double)prefs.followCacheTargetBytes());
	}

	/**
	 * Sums all the element data in the given followIndex.
	 * 
	 * @param followees The index to sum.
	 * @return The sum of all bytes in the cache.
	 */
	public static long getCurrentCacheSizeBytes(IFolloweeReading followees)
	{
		long sizeBytes = 0L;
		// Note that the cache allows for double-counting so we need to check every element from each followee, not just the raw list.
		for (IpfsKey key : followees.getAllKnownFollowees())
		{
			for (FollowingCacheElement elt : followees.snapshotAllElementsForFollowee(key).values())
			{
				sizeBytes += elt.combinedSizeBytes();
			}
		}
		return sizeBytes;
	}

	public static void pruneCacheIfNeeded(IWritingAccess access, IFolloweeWriting followees, CacheAlgorithm algorithm, long bytesToAdd) throws IpfsConnectionException
	{
		if (algorithm.needsCleanAfterAddition(bytesToAdd))
		{
			// Create the initial list of eviction candidates (this list doesn't favour any specific edge since eviction
			// is random).
			// Note that we don't handle duplicates across different followees, or even within the same followee, as
			// being special - we allow them to be double-counted so they must be double-evicted.
			List<CacheAlgorithm.Candidate<Pair<IpfsKey, FollowingCacheElement>>> evictionCandidates = new ArrayList<>();
			for (IpfsKey key : followees.getAllKnownFollowees())
			{
				for (FollowingCacheElement elt : followees.snapshotAllElementsForFollowee(key).values())
				{
					evictionCandidates.add(new CacheAlgorithm.Candidate<>(elt.combinedSizeBytes(), new Pair<>(key, elt)));
				}
			}
			
			// Now, see which elements we should evict.
			List<CacheAlgorithm.Candidate<Pair<IpfsKey, FollowingCacheElement>>> evictions = algorithm.toRemoveInResize(evictionCandidates);
			
			// We can walk this list once, doing the leaf unpinning and cache updates as we go (since we don't care about anything failing here).
			for (CacheAlgorithm.Candidate<Pair<IpfsKey, FollowingCacheElement>> eviction : evictions)
			{
				IpfsKey followee = eviction.data().first();
				FollowingCacheElement element = eviction.data().second();
				
				// Unpin the leaf elements.
				IpfsFile imageHash = element.imageHash();
				if (null != imageHash)
				{
					access.unpin(imageHash);
				}
				IpfsFile leafHash = element.leafHash();
				if (null != leafHash)
				{
					access.unpin(leafHash);
				}
				
				// Clean up the cache.
				followees.removeElement(followee, element.elementHash());
			}
		}
	}
}
