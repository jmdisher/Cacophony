package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;


/**
 * A high-level projection of the prefs data which also exposes a mutation interface.
 * We use a mutation interface instead of just making this is a writable struct-like type since we want to investigate
 * an event stream approach, later.
 */
public class PrefsData
{
	public static PrefsData buildOnPrefs(GlobalPrefs prefs)
	{
		return new PrefsData(prefs.videoEdgePixelMax(), prefs.followCacheTargetBytes());
	}


	// Note that this is currently a trivial re-interpretation of GlobalPrefs, just meant to decouple us from the serialized form.
	private int _videoEdgePixelMax;
	private long _followCacheTargetBytes;

	private PrefsData(int videoEdgePixelMax
			, long followCacheTargetBytes
	)
	{
		_videoEdgePixelMax = videoEdgePixelMax;
		_followCacheTargetBytes = followCacheTargetBytes;
	}

	public GlobalPrefs serializeToPrefs()
	{
		return new GlobalPrefs(_videoEdgePixelMax
				, _followCacheTargetBytes
		);
	}

	public int videoEdgePixelMax()
	{
		return _videoEdgePixelMax;
	}

	public void setVideoEdgePixelMax(int videoEdgePixelMax)
	{
		_videoEdgePixelMax = videoEdgePixelMax;
	}

	public long followCacheTargetBytes()
	{
		return _followCacheTargetBytes;
	}

	public void setFollowCacheTargetBytes(long followCacheTargetBytes)
	{
		_followCacheTargetBytes = followCacheTargetBytes;
	}
}
