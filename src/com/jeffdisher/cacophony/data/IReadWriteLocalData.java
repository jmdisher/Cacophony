package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;


/**
 * Expands upon IReadOnlyLocalData by adding the corresponding write operations.
 * Note that write operations are local to the receiver, and can be seen on later read of the written data, but are not
 * written-back into durable storage until the receiver is closed.
 */
public interface IReadWriteLocalData extends IReadOnlyLocalData
{
	void writeLocalIndex(ChannelData index);
	void writeGlobalPrefs(PrefsData prefs);
	void writeGlobalPinCache(PinCacheData pinCache);
	void writeFollowIndex(FolloweeData followIndex);
	ExplicitCacheData readExplicitCache();
	void writeExplicitCache(ExplicitCacheData explicitCache);
	void writeFavouritesCache(FavouritesCacheData favouritesCache);
}
