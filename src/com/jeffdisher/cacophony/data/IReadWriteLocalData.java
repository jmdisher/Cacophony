package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;


/**
 * Expands upon IReadOnlyLocalData by adding the corresponding write operations.
 * Note that write operations are local to the receiver, and can be seen on later read of the written data, but are not
 * written-back into durable storage until the receiver is closed.
 */
public interface IReadWriteLocalData extends IReadOnlyLocalData
{
	void writeLocalIndex(LocalIndex index);
	void writeGlobalPrefs(GlobalPrefs prefs);
	void writeGlobalPinCache(GlobalPinCache pinCache);
	void writeFollowIndex(FollowIndex followIndex);
}
