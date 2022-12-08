package com.jeffdisher.cacophony.projection;

import java.util.List;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * NOTE:  This data only describes records which have leaf elements cached (as they are the only ones which take up
 * space in the cache).
 * All record meta-data XML which is below the size limit, reachable from the most recent root cached for each followed
 * key is also pinned on the local node but are not tracked in the local data model, so they need to be fetched from the
 * network.
 */
public interface IFolloweeReading
{
	/**
	 * @return The keys of all the followees we are currently following.
	 */
	Set<IpfsKey> getAllKnownFollowees();

	/**
	 * @param publicKey The key of the followee.
	 * @param cid The CID of the StreamRecord element.
	 * @return The element associated with this CID for this followee or null, if not cached.
	 */
	FollowingCacheElement getElementForFollowee(IpfsKey publicKey, IpfsFile cid);

	/**
	 * @param publicKey The key of the followee.
	 * @return The CID of the last fetched StreamIndex file for this followee.
	 */
	IpfsFile getLastFetchedRootForFollowee(IpfsKey publicKey);

	/**
	 * Used only by tests as this is otherwise just used for internal decision-making.
	 * 
	 * @param publicKey The key of the followee.
	 * @return The last time we polled for updates for this followee.
	 */
	long getLastPollMillisForFollowee(IpfsKey publicKey);

	/**
	 * @param publicKey The key of the followee.
	 * @return The in-order list of StreamRecords elements cached for this followee, more recent entries at the end.
	 */
	List<IpfsFile> getElementsForFollowee(IpfsKey publicKey);

	/**
	 * @return The next followee key we should poll.
	 */
	IpfsKey getNextFolloweeToPoll();
}
