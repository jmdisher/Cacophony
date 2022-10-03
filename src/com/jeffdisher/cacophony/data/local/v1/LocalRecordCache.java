package com.jeffdisher.cacophony.data.local.v1;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This is used as a container of StreamRecord instances which we know something about locally.  This includes the
 * records authored by the channel owner as well as the records authored by users they are following.
 * This cache exists to avoid needing to do a lot of reaching into the IPFS node, from the root StreamIndex CIDs, for
 * every lookup of a specific record (since that is a common operation).
 * The cache will become invalid if the FolloweeIndex changes or if the channel owner modifies their own stream.
 */
public class LocalRecordCache
{
	private final Map<IpfsFile, Element> _cache;

	/**
	 * Creates the new cache with a copy of the given map (as the cache is essentially just a wrapper over a map to
	 * provide context and a leaf element type).
	 * 
	 * @param cache The map to copy into the cache.
	 */
	public LocalRecordCache(Map<IpfsFile, Element> cache)
	{
		// Create a duplicate of the map.
		_cache = new HashMap<>(cache);
	}

	/**
	 * @return The set of all StreamRecord CIDs known to the cache.
	 */
	public Set<IpfsFile> getKeys()
	{
		return Collections.unmodifiableSet(_cache.keySet());
	}

	/**
	 * @param cid The CID of the StreamRecord to look up.
	 * @return The cached data for this StreamRecord or null if it isn't in the cache.
	 */
	public Element get(IpfsFile cid)
	{
		return _cache.get(cid);
	}


	/**
	 * A description of the data we have cached for a given StreamRecord.
	 */
	public static record Element(String name, String description, long publishedSecondsUtc, String discussionUrl, boolean isCached, String thumbnailUrl, String videoUrl)
	{
	}
}
