package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
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

	/**
	 * Prunes the followee cache until it is under its limit.
	 * 
	 * @param access Write-access.
	 * @param followees The followees data.
	 * @param currentCacheSizeBytes The current size of followee data in the cache.
	 * @param limitSizeBytes The limit we want to fit under.
	 * @throws IpfsConnectionException There was a problem unpinning cached elements.
	 */
	public static void pruneCache(IWritingAccess access, FolloweeData followees, long currentCacheSizeBytes, long limitSizeBytes) throws IpfsConnectionException
	{
		// This should only be called if the cache is above this limit.
		Assert.assertTrue(currentCacheSizeBytes > limitSizeBytes);
		
		// Create the initial list of eviction candidates (this list doesn't favour any specific edge since eviction
		// is random).
		// Note that we don't handle duplicates across different followees, or even within the same followee, as
		// being special - we allow them to be double-counted so they must be double-evicted.
		List<CacheAlgorithm.Candidate<Pair<IpfsKey, FollowingCacheElement>>> evictionCandidates = new ArrayList<>();
		for (IpfsKey key : followees.getAllKnownFollowees())
		{
			for (FollowingCacheElement elt : followees.snapshotAllElementsForFollowee(key).values())
			{
				// We only consider this a candidate if it has attached leaves (a non-zero combined size).
				long combinedSize = elt.combinedSizeBytes();
				if (combinedSize > 0L)
				{
					evictionCandidates.add(new CacheAlgorithm.Candidate<>(combinedSize, new Pair<>(key, elt)));
				}
			}
		}
		
		// Now, see which elements we should evict.
		CacheAlgorithm algorithm = new CacheAlgorithm(limitSizeBytes, currentCacheSizeBytes);
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
			
			// Clean up the cache - we want to remove the existing cache element and replace it with one which only has the meta-data.
			followees.removeElement(followee, element.elementHash());
			followees.addElement(followee, new FollowingCacheElement(element.elementHash(), null, null, 0L));
		}
	}
}
