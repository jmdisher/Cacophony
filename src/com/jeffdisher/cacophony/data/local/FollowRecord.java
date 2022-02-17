package com.jeffdisher.cacophony.data.local;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The top-level record in the FollowIndex, recording all the data associated with a public key we are following.
 */
public record FollowRecord(IpfsKey publicKey, IpfsFile lastFetchedRoot, long lastPollMillis, FollowingCacheElement[] elements) implements Serializable
{
}
