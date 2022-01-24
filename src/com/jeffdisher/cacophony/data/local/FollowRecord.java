package com.jeffdisher.cacophony.data.local;

import java.io.Serializable;


/**
 * The top-level record in the FollowIndex, recording all the data associated with a public key we are following.
 */
public record FollowRecord(String publicKey, String lastFetchedRoot, long lastPollMillis, FollowingCacheElement[] elements) implements Serializable
{
}
