package com.jeffdisher.cacophony.data.local.v1;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The top-level record in the FollowIndex, recording all the data associated with a public key we are following.
 * Note that we always pin every meta-data entry referenced by a user we are following (so, every record) but we only
 * add FollowingCacheElement instances for the elements where we also decided to pin some leaf data elements (thumbnail,
 * video, etc).
 * We typically add new FollowingCacheElement instances to the end of the elements array.
 */
public record FollowRecord(IpfsKey publicKey, IpfsFile lastFetchedRoot, long lastPollMillis, FollowingCacheElement[] elements) implements Serializable
{
}
