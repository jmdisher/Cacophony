package com.jeffdisher.cacophony.data;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;


/**
 * The interface for read-only actions against the local data storage.
 * Note that the returned objects are either read-only or are clones of internal data to ensure that modifications to
 * them are either not possible or are only local to their own instance, not impacting other callers.
 * This also means that it is possible to create an ABA problem if a read-lock is acquired, released, and then a
 * write-lock is acquired to write-back the updates:  It is possible that another write happened in between.
 */
public interface IReadOnlyLocalData extends AutoCloseable
{
	ChannelData readLocalIndex();
	PrefsData readGlobalPrefs();
	PinCacheData readGlobalPinCache();
	FolloweeData readFollowIndex();
	FavouritesCacheData readFavouritesCache();
	/**
	 * We implement AudoCloseable so we can use the try-with-resources idiom but we have no need for the exception so
	 * we override the close() not to throw it.
	 */
	void close();
}
