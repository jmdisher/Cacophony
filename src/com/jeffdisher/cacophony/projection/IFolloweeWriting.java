package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The mutative interface for the followee data store.
 * Note that the implementation is free to write-back these changes immediately or wait for a commit decision, based on
 * its intended use.
 */
public interface IFolloweeWriting extends IFolloweeReading
{
	/**
	 * Adds a new element for the given followee.
	 * Asserts that an element with the same elementHash is not already in the cache for this followee.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param element The new element to track.
	 */
	void addElement(IpfsKey followeeKey, FollowingCacheElement element);

	/**
	 * Removes the element from the tracking for this followee.
	 * If the followee isn't already tracking this element, this method does nothing.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param elementCid The CID of the StreamRecord to drop from the cache.
	 */
	void removeElement(IpfsKey followeeKey, IpfsFile elementCid);

	/**
	 * Creates a new followee record, internally.  This must be called before this followeeKey can be used in any other
	 * calls in this interface.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The initial StreamIndex CID.
	 * @param lastPollMillis The current time.
	 */
	void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis);

	/**
	 * Updates an existing followee's record.  Assumes that the followee already exists.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The StreamIndex CID of the most recent refresh of the followee.
	 * @param lastPollMillis The current time.
	 */
	void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis);

	/**
	 * Removes a given followee entirely from tracking.  Note that this call assumes there are no elements associated
	 * with this followee and that the record does already exist.
	 * 
	 * @param followeeKey The public key of the followee.
	 */
	void removeFollowee(IpfsKey followeeKey);

	/**
	 * Attaches the listener for followee refresh data updates.  This can be called at most once for any given instance.
	 * Upon being called, the given followeeRefreshConnector will be populated with the current state of followee data
	 * and will then be notified of refresh times whenever a followee is added/removed/refreshed.
	 * 
	 * @param followeeRefreshConnector The connector to notify of followee refreshes.
	 */
	void attachRefreshConnector(HandoffConnector<IpfsKey, Long> followeeRefreshConnector);
}
