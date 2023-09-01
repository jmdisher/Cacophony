package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The read-only part of the LocalRecordCache interface.
 */
public interface ILocalRecordCache
{
	/**
	 * @param cid The CID of the StreamRecord to look up.
	 * @return The cached data for this StreamRecord or null if it isn't in the cache.
	 */
	public CachedRecordInfo get(IpfsFile cid);
}
