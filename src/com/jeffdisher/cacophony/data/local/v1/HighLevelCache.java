package com.jeffdisher.cacophony.data.local.v1;

import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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


	private final GlobalPinCache _globalPinCache;
	private final INetworkScheduler _scheduler;

	public HighLevelCache(GlobalPinCache globalPinCache, INetworkScheduler scheduler)
	{
		_globalPinCache = globalPinCache;
		_scheduler = scheduler;
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
	 * @throws IpfsConnectionException There was a problem contacting the IPFS server.
	 */
	public void removeFromThisCache(IpfsFile cid) throws IpfsConnectionException
	{
		_unpinAndRemove(cid);
	}

	public void addToFollowCache(IpfsKey channel, Type type, IpfsFile cid) throws IpfsConnectionException
	{
		// TODO: Do the accounting for the size per-follower.
		boolean shouldPin = _globalPinCache.shouldPinAfterAdding(cid);
		if (shouldPin)
		{
			try
			{
				_scheduler.pin(cid).get();
			}
			catch (IpfsConnectionException e)
			{
				// If we failed to pin, we should revert the change to the pin cache.
				Assert.assertTrue(_globalPinCache.shouldUnpinAfterRemoving(cid));
				throw e;
			}
		}
	}

	public void removeFromFollowCache(IpfsKey channel, Type type, IpfsFile cid) throws IpfsConnectionException
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


	private void _unpinAndRemove(IpfsFile cid) throws IpfsConnectionException
	{
		boolean shouldUnpin = _globalPinCache.shouldUnpinAfterRemoving(cid);
		if (shouldUnpin)
		{
			// Note that we initially thought that these unpin operations should be deferred so that the core logic
			// could just unpin whenever was most convenient and they would always survive in the cache until after the
			// command.  However, the feature was delayed so all the commands were implemented using a strictly in-order
			// cache, hence this delayed processing was never required so we always unpin, inline.
			_scheduler.unpin(cid).get();
		}
	}
}
