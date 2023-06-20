package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.projection.ExplicitCacheData.UserInfo;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The read-only subset of ExplicitCacheData's interface.
 * The implementation must either not change state or correctly lock against concurrent access since callers will only
 * have the shared storage read lock.
 */
public interface IExplicitCacheReading
{
	/**
	 * Reads the UserInfo of the given user's indexCid.  On success, marks the record as most recently used.
	 * Note that this uses the _lruLock since it changes the LRU order, despite being a read-only call.
	 * 
	 * @param indexCid The CID of the user's StreamIndex.
	 * @return The UserInfo for the user (null if not found).
	 */
	UserInfo getUserInfo(IpfsFile indexCid);

	/**
	 * Reads the CachedRecordInfo of the given StreamRecord's recordCid.  On success, marks the record as most recently used.
	 * Note that this uses the _lruLock since it changes the LRU order, despite being a read-only call.
	 * 
	 * @param recordCid The CID of the StreamRecord.
	 * @return The CachedRecordInfo for the record (null if not found).
	 */
	CachedRecordInfo getRecordInfo(IpfsFile recordCid);

	/**
	 * @return The total size of the explicit cache, in bytes.
	 */
	long getCacheSizeBytes();
}
