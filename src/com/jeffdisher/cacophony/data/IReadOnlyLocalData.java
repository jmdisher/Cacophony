package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;


/**
 * The interface for read-only actions against the local data storage.
 * Note that the returned objects are either read-only or are clones of internal data to ensure that modifications to
 * them are either not possible or are only local to their own instance, not impacting other callers.
 * This also means that it is possible to create an ABA problem if a read-lock is acquired, released, and then a
 * write-lock is acquired to write-back the updates:  It is possible that another write happened in between.
 */
public interface IReadOnlyLocalData extends AutoCloseable
{
	LocalIndex readLocalIndex();
	GlobalPrefs readGlobalPrefs();
	GlobalPinCache readGlobalPinCache();
	FollowIndex readFollowIndex();
	/**
	 * We implement AudoCloseable so we can use the try-with-resources idiom but we have no need for the exception so
	 * we override the close() not to throw it.
	 */
	void close();
}
