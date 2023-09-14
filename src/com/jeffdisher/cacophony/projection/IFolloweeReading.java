package com.jeffdisher.cacophony.projection;

import java.util.Map;
import java.util.Set;

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
	 * Grants access to the internal map of elements known for a given followee.  Note that this returned map is
	 * NOT connected to the internal state of the callee.
	 * 
	 * @param publicKey The key of the followee.
	 * @return A copy of all the elements cached for the given followee or null, if it is not known.
	 */
	Map<IpfsFile, FollowingCacheElement> snapshotAllElementsForFollowee(IpfsKey publicKey);

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
	 * @return The next followee key we should poll.
	 */
	IpfsKey getNextFolloweeToPoll();

	/**
	 * @param publicKey The key of the followee.
	 * @return The next target record for incremental synchronization (null if incremental synchronization is complete).
	 */
	IpfsFile getNextBackwardRecord(IpfsKey followeeKey);

	/**
	 * Looks up the set of previously skipped records for the given followeeKey.  If temporaryOnly, will only return
	 * the temporarily skipped elements whereas passing false will return all of the skipped elements.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param temporaryOnly True if only temporarily skipped elements should be returned (false returns all skipped
	 * elements).
	 * @return The set of skipped records.
	 */
	Set<IpfsFile> getSkippedRecords(IpfsKey followeeKey, boolean temporaryOnly);
}
