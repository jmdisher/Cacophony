package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.data.local.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Miscellaneous helper functions related to follower cache management.
 */
public class CacheHelpers
{
	public static Map<String, FollowingCacheElement> createCachedMap(FollowRecord record)
	{
		return Arrays.stream(record.elements()).collect(Collectors.toMap(FollowingCacheElement::elementHash, Function.identity()));
	}

	public static void addElementToCache(RemoteActions remote, HighLevelCache cache, FollowIndex followIndex, IpfsKey followeeKey, IpfsFile fetchedRoot, int videoEdgePixelMax, long currentTimeMillis, String rawCid) throws IOException
	{
		IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
		cache.addToFollowCache(followeeKey, HighLevelCache.Type.METADATA, cid);
		// We will go through the elements, looking for the special image and the last, largest video element no larger than our resolution limit.
		IpfsFile imageHash = null;
		IpfsFile leafHash = null;
		int biggestEdge = 0;
		StreamRecord record = GlobalData.deserializeRecord(remote.readData(cid));
		for (DataElement elt : record.getElements().getElement())
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
		long combinedSizeBytes = 0L;
		if (null != imageHash)
		{
			cache.addToFollowCache(followeeKey, HighLevelCache.Type.FILE, imageHash);
			combinedSizeBytes += remote.getSizeInBytes(imageHash);
		}
		if (null != leafHash)
		{
			cache.addToFollowCache(followeeKey, HighLevelCache.Type.FILE, leafHash);
			combinedSizeBytes += remote.getSizeInBytes(leafHash);
		}
		followIndex.addNewElementToFollower(followeeKey, fetchedRoot, cid, imageHash, leafHash, currentTimeMillis, combinedSizeBytes);
	}
}
