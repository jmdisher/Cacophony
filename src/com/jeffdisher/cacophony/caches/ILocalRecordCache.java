package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The read-only part of the LocalRecordCache interface.
 */
public interface ILocalRecordCache
{
	/**
	 * @param cid The CID of the StreamRecord to look up.
	 * @return The cached data for this StreamRecord or null if it isn't in the cache.
	 */
	public Element get(IpfsFile cid);


	/**
	 * A description of the data we have cached for a given StreamRecord.
	 * This is the version of the data which is considered valid for external consumption as it avoids internal details.
	 */
	public static record Element(boolean isCached
			, String name
			, String description
			, long publishedSecondsUtc
			, String discussionUrl
			, IpfsKey publisherKey
			, IpfsFile replyToCid
			, IpfsFile thumbnailCid
			, IpfsFile videoCid
			, IpfsFile audioCid
	)
	{
	}
}
