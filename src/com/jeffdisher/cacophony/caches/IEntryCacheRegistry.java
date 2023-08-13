package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The read-only part of the EntryCacheRegistry interface.
 */
public interface IEntryCacheRegistry
{
	/**
	 * Requests the reference to the connector for the given user key (followee or local).
	 * 
	 * @param key The user's key.
	 * @return The connector for this user.
	 */
	public HandoffConnector<IpfsFile, Void> getReadOnlyConnector(IpfsKey key);

	/**
	 * Requests the reference to the combined connector for all registered users.
	 * 
	 * @return The combined connector instance.
	 */
	public HandoffConnector<IpfsFile, Void> getCombinedConnector();

}
