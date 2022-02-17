package com.jeffdisher.cacophony.data.local;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The record of a single element of data stored locally.
 * The hash of the element is stored as well as the special image hash (can be null) and the leaf data node.
 * The leaf node refers to the specific file actually hashed since there are often multiple options but a given follower
 * should only cache the one they care about.
 */
public record FollowingCacheElement(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
{
}
