package com.jeffdisher.cacophony.access;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The aspects of the access design which require writing to the local storage (even if they seem network-only - some
 * network operations require updates to local storage state).
 */
public interface IWritingAccess extends IReadingAccess
{
	/**
	 * Updates the root index hash in the local storage.
	 * 
	 * @param newIndexHash The new IPFS file location.
	 */
	void updateIndexHash(IpfsFile newIndexHash);

	// TEMP
	HighLevelCache loadCacheReadWrite() throws IpfsConnectionException;

	// TEMP.
	FollowIndex readWriteFollowIndex();
}
