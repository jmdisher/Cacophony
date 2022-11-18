package com.jeffdisher.cacophony.access;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * The aspects of the access design which require read access to the local storage (even if they seem network-only -
 * some network operations require reading the local storage state).
 */
public interface IReadingAccess extends AutoCloseable
{
	/**
	 * We implement AudoCloseable so we can use the try-with-resources idiom but we have no need for the exception so
	 * we override the close() not to throw it.
	 */
	void close();

	// TEMP.
	LocalIndex readOnlyLocalIndex();

	// TEMP.
	INetworkScheduler scheduler() throws IpfsConnectionException;

	// TEMP.
	HighLevelCache loadCacheReadOnly() throws IpfsConnectionException;

	// TEMP.
	FollowIndex readOnlyFollowIndex();

	/**
	 * @return The preferences for this channel.
	 */
	GlobalPrefs readGlobalPrefs();
}
