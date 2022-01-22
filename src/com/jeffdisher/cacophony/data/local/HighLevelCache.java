package com.jeffdisher.cacophony.data.local;

import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * High-level helpers for interacting with the local cache(s).
 * Note that there a different types of high-level cache domains:
 * 1) this channel - the cache is never pruned, only sometimes purged of deleted entries
 * 2) a channel we are following - the cache is periodically pruned
 * 3) a data element explicitly added for direct viewing - the cache will be short-lived
 * 4) a data element was added only to peruse information on the network - this cache will be aggressively pruned
 * 
 * An important point to remember is that none of the caches are immediately pruned so delete actions can be issued
 * before waiting for any replacement data to propagate.
 */
public class HighLevelCache
{
	/**
	 * The type of reference being added.
	 */
	public enum Type
	{
		/**
		 * A CID which points to a Cacophony XML snippet used to describe channel structure and contents.
		 */
		METADATA,
		/**
		 * A leaf node file on IPFS.
		 */
		FILE,
	}

	public static HighLevelCache fromLocal(LocalActions local)
	{
		// TODO: Implement
		return new HighLevelCache();
	}

	public void addToThisCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void removeFromThisCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void addToFollowCache(IpfsKey channel, Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void removeFromFollowCache(IpfsKey channel, Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void addToExplicitCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void removeFromExplicitCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void addToTempCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}

	public void removeFromTempCache(Type type, IpfsFile cid)
	{
		// TODO: Implement
	}
}
