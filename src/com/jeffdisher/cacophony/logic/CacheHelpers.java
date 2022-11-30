package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.CacheAlgorithm.Candidate;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Miscellaneous helper functions related to follower cache management.
 */
public class CacheHelpers
{
	// We currently want to make sure the cache is at most 90% full before caching a new channel.
	private static final double CACHE_MAX_MULTIPLIER_PRE_NEW_CHANNEL = 0.90;

	public static Map<IpfsFile, FollowingCacheElement> createCachedMap(FollowRecord record)
	{
		return Arrays.stream(record.elements()).collect(Collectors.toMap(FollowingCacheElement::elementHash, Function.identity()));
	}

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
	 * @param followIndex The index to sum.
	 * @return The sum of all bytes in the cache.
	 */
	public static long getCurrentCacheSizeBytes(FollowIndex followIndex)
	{
		long sizeBytes = 0L;
		for (FollowRecord record : followIndex)
		{
			sizeBytes += Stream.of(record.elements()).mapToLong((elt) -> elt.combinedSizeBytes()).sum();
		}
		return sizeBytes;
	}

	/**
	 * Finds a list of candidates for evictions.
	 * 
	 * @param followIndex The index to search.
	 * @return The list of candidates which should be considered for eviction.
	 */
	public static List<Candidate<FollowingCacheElement>> getEvictionCandidateList(FollowIndex followIndex)
	{
		List<Candidate<FollowingCacheElement>> candidates = new ArrayList<>();
		for (FollowRecord record : followIndex)
		{
			for (FollowingCacheElement elt : record.elements())
			{
				candidates.add(new CacheAlgorithm.Candidate<FollowingCacheElement>(elt.combinedSizeBytes(), elt));
			}
		}
		return candidates;
	}

	public static void pruneCacheIfNeeded(IWritingAccess access, FollowIndex followIndex, CacheAlgorithm algorithm, long bytesToAdd) throws IpfsConnectionException
	{
		if (algorithm.needsCleanAfterAddition(bytesToAdd))
		{
			Map<IpfsKey, FollowingCacheElement[]> toReplace = new HashMap<>();
			for (FollowRecord record : followIndex)
			{
				// TODO:  This algorithm needs to be replaced since it is reaching into the FollowIndex.
				List<FollowingCacheElement> retained = new ArrayList<>();
				for (FollowingCacheElement elt : record.elements())
				{
					if (algorithm.toRemoveInResize(List.of(new CacheAlgorithm.Candidate<FollowingCacheElement>(elt.combinedSizeBytes(), elt))).isEmpty())
					{
						// We are NOT removing this element so keep it in the list.
						retained.add(elt);
					}
					else
					{
						IpfsFile imageHash = elt.imageHash();
						if (null != imageHash)
						{
							access.unpin(imageHash);
						}
						IpfsFile leafHash = elt.leafHash();
						if (null != leafHash)
						{
							access.unpin(leafHash);
						}
					}
					// NOTE:  We always leave the meta-data cached (the StreamRecord) - only the leaf elements are prunable since they are the large files.
				}
				if (record.elements().length != retained.size())
				{
					toReplace.put(record.publicKey(), retained.toArray((int size) -> new FollowingCacheElement[size]));
				}
			}
			for (IpfsKey key : toReplace.keySet())
			{
				FollowingCacheElement[] value = toReplace.get(key);
				followIndex.replaceCached(key, value);
			}
		}
	}
}
