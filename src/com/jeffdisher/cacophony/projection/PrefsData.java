package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v2.Opcode_SetPrefsKey;


/**
 * A high-level projection of the prefs data which also exposes a mutation interface.
 * We use a mutation interface instead of just making this is a writable struct-like type since we want to investigate
 * an event stream approach, later.
 */
public class PrefsData
{
	public static final String INT_VIDEO_EDGE = "INT_VIDEO_EDGE";
	public static final String LONG_FOLLOW_CACHE_BYTES = "LONG_FOLLOW_CACHE_BYTES";

	public static PrefsData buildOnPrefs(GlobalPrefs prefs)
	{
		return new PrefsData(prefs.videoEdgePixelMax(), prefs.followCacheTargetBytes());
	}

	public static PrefsData defaultPrefs()
	{
		GlobalPrefs prefs = GlobalPrefs.defaultPrefs();
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

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		stream.writeObject(new Opcode_SetPrefsKey(INT_VIDEO_EDGE, Integer.valueOf(_videoEdgePixelMax)));
		stream.writeObject(new Opcode_SetPrefsKey(LONG_FOLLOW_CACHE_BYTES, Long.valueOf(_followCacheTargetBytes)));
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
