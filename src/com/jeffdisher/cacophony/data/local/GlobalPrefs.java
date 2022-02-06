package com.jeffdisher.cacophony.data.local;

import java.io.Serializable;


public record GlobalPrefs(int videoEdgePixelMax, long followCacheTargetBytes) implements Serializable
{
	// We will default to 720p, which is 720/1280, so we use 1280 as the edge size.
	private static final int VIDEO_EDGE_LIMIT = 1280;
	// We will start with a follower cache default target size of 10 GB (probably too small but not ultra-tiny).
	private static final long FOLLOWING_CACHE_TARGET_BYTES = 10_000_000_000L;

	public static GlobalPrefs defaultPrefs()
	{
		return new GlobalPrefs(VIDEO_EDGE_LIMIT, FOLLOWING_CACHE_TARGET_BYTES);
	}
}
