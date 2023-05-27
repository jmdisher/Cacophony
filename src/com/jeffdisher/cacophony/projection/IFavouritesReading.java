package com.jeffdisher.cacophony.projection;

import java.util.List;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The read-only subset of operations implemented by FavouritesCacheData.
 */
public interface IFavouritesReading
{
	/**
	 * Looks up the requested record info.
	 * 
	 * @param recordCid The CID of the StreamRecord.
	 * @return The cached info about the record and leaves, or null if it isn't in cache.
	 */
	CachedRecordInfo getRecordInfo(IpfsFile recordCid);

	/**
	 * @return The list of all record files in the cache, in the order they were added.
	 */
	List<IpfsFile> getRecordFiles();
}
