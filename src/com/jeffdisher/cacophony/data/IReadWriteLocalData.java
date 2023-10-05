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
	/**
	 * @param index The changed home channel data.
	 */
	void writeLocalIndex(ChannelData index);
	/**
	 * @param prefs The changed prefs data.
	 */
	void writeGlobalPrefs(PrefsData prefs);
	/**
	 * @param pinCache The changed global pin cache.
	 */
	void writeGlobalPinCache(PinCacheData pinCache);
	/**
	 * @param followIndex The changed followee data.
	 */
	void writeFollowIndex(FolloweeData followIndex);
	/**
	 * @param explicitCache The changed explicit cache.
	 */
	void writeExplicitCache(ExplicitCacheData explicitCache);
	/**
	 * @param favouritesCache The changed favourites data.
	 */
	void writeFavouritesCache(FavouritesCacheData favouritesCache);
}
