package com.jeffdisher.cacophony.data.local;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.IPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * High-level helpers for interacting with the local cache(s) and writing-back any changes to the pin/unpin state of the
 * local node.
 * From a design perspective, this class is meant to represent what is cached on the local node and, more importantly,
 * what we know is cached on the local node.
 * 
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
	public static enum Type
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

	public static HighLevelCache fromLocal(ILocalActions local)
	{
		// We need to know 3 primary things in this object:
		// 1) The global pin cache - to determine when to pin/unpin.
		// 2) The IPFS connection's pin abstraction - to request pin/unpin.
		// 3) Our other data caches - to determine when to modify the global pin cache.
		return new HighLevelCache(local.loadGlobalPinCache(), local.getSharedPinMechanism());
	}


	private final GlobalPinCache _globalPinCache;
	private final IPinMechanism _localNode;

	public HighLevelCache(GlobalPinCache globalPinCache, IPinMechanism localNode)
	{
		_globalPinCache = globalPinCache;
		_localNode = localNode;
	}

	/**
	 * Adds the given cid to the cache, treating it as reachable from this channel, and having just been explicitly
	 * uploaded.
	 * The reason for distinguishing the case of this channel from other channels is that this channel is not restricted
	 * or periodically garbage collected, but only unpins entries explicitly, and the updates are always explicitly
	 * uploaded, as opposed to pinned.
	 * 
	 * @param cid The IPFS CID of the meta-data or file entry just uploaded.
	 */
	public void uploadedToThisCache(IpfsFile cid)
	{
		// NOTE:  We will eventually need to handle the case where this cache "re-broadcasts" an element from a different channel.
		_globalPinCache.hashWasAdded(cid);
	}

	/**
	 * Removes the given cid from the cache, treating it as previously reachable from this channel.
	 * The reason for distinguishing this case from the general cases is that it isn't tracked in any other cache
	 * system.
	 * 
	 * @param cid The IPFS CID of the meta-data or file entry no longer reachable from this channel.
	 */
	public void removeFromThisCache(IpfsFile cid)
	{
		_unpinAndRemove(cid);
	}

	public void addToFollowCache(IpfsKey channel, Type type, IpfsFile cid)
	{
		// TODO: Do the accounting for the size per-follower.
		boolean shouldPin = _globalPinCache.shouldPinAfterAdding(cid);
		if (shouldPin)
		{
			try
			{
				_localNode.add(cid);
			}
			catch (IOException e)
			{
				// TODO: Determine how to handle this.
				throw Assert.unexpected(e);
			}
		}
	}

	public void removeFromFollowCache(IpfsKey channel, Type type, IpfsFile cid)
	{
		// TODO: Do the accounting for the size per-follower.
		_unpinAndRemove(cid);
	}

	public void addToExplicitCache(Type type, IpfsFile cid)
	{
		Assert.unimplemented(3);
	}

	public void removeFromExplicitCache(Type type, IpfsFile cid)
	{
		Assert.unimplemented(3);
	}

	public void addToTempCache(Type type, IpfsFile cid)
	{
		Assert.unimplemented(3);
	}

	public void removeFromTempCache(Type type, IpfsFile cid)
	{
		Assert.unimplemented(3);
	}


	private void _unpinAndRemove(IpfsFile cid)
	{
		boolean shouldUnpin = _globalPinCache.shouldUnpinAfterRemoving(cid);
		if (shouldUnpin)
		{
			// TODO:  Add this to our list of unpins to perform later - we want these to lag.
			try
			{
				_localNode.rm(cid);
			}
			catch (IOException e)
			{
				// TODO: Determine how to handle this.
				throw Assert.unexpected(e);
			}
		}
	}
}
