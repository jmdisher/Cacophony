package com.jeffdisher.cacophony.data.local.v1;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The record of a single element of data stored locally.
 * The hash of the element is stored as well as the special image hash (can be null) and the leaf data node.
 * The leaf node refers to the specific file actually hashed since there are often multiple options but a given follower
 * should only cache the one they care about.
 * NOTE:  combinedSizeBytes includes only the size of the data pointed to by imageHash and leafHash, NOT elementHash.
 * This is due to the design of the followee cache ALWAYS caching all meta-data (hence the StreamRecord referenced by
 * elementHash), so only the leaf data is counted toward the followee cache occupancy.
 * This is an important difference as compared to CachedRecordInfo.
 */
public record FollowingCacheElement(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
{
}
