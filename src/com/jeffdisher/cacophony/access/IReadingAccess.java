package com.jeffdisher.cacophony.access;

import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


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

	// TEMP.
	IConnection connection() throws IpfsConnectionException;

	// TEMP.
	LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator);

	// TEMP - only used for tests.
	boolean isInPinCached(IpfsFile file);

	/**
	 * @return The preferences for this channel.
	 */
	GlobalPrefs readGlobalPrefs();

	/**
	 * Requests that the IPFS node reclaim storage.
	 * @throws IpfsConnectionException There was an error connecting to IPFS.
	 */
	void requestIpfsGc() throws IpfsConnectionException;
}
