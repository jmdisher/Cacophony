package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v2.Opcode_SetPrefsKey;


/**
 * A high-level projection of the prefs data which also exposes an interface to serialize itself into data opcodes.
 */
public class PrefsData
{
	public static final String INT_VIDEO_EDGE = "INT_VIDEO_EDGE";
	public static final String LONG_FOLLOW_CACHE_BYTES = "LONG_FOLLOW_CACHE_BYTES";
	public static final String LONG_REPUBLISH_INTERVAL_MILLIS = "LONG_REPUBLISH_INTERVAL_MILLIS";
	public static final String LONG_FOLLOWEE_REFRESH_MILLIS = "LONG_FOLLOWEE_REFRESH_MILLIS";

	// We will default to 720p, which is 720/1280, so we use 1280 as the edge size.
	public static final int DEFAULT_VIDEO_EDGE = 1280;
	// We will start with a follower cache default target size of 10 GB (probably too small but not ultra-tiny).
	// (this is mutable since we override it in some tests).
	public static long DEFAULT_FOLLOW_CACHE_BYTES = 10_000_000_000L;
	// The public key publications seem to be valid for about 24 hours, by default, so we will use 12.
	public static final long DEFAULT_REPUBLISH_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L;
	// We will default to refreshing each followeee once per hour.
	public static final long DEFAULT_FOLLOWEE_REFRESH_MILLIS = 60L * 60L * 1000L;


	public static PrefsData defaultPrefs()
	{
		PrefsData prefs = new PrefsData();
		prefs.videoEdgePixelMax = DEFAULT_VIDEO_EDGE;
		prefs.followCacheTargetBytes = DEFAULT_FOLLOW_CACHE_BYTES;
		prefs.republishIntervalMillis = DEFAULT_REPUBLISH_INTERVAL_MILLIS;
		prefs.followeeRefreshMillis = DEFAULT_FOLLOWEE_REFRESH_MILLIS;
		return prefs;
	}


	// These are exposed just as public fields since this is effectively a mutable struct.
	public int videoEdgePixelMax;
	public long followCacheTargetBytes;
	public long republishIntervalMillis;
	public long followeeRefreshMillis;

	// We keep this private just so the factory is used to explicitly create the defaults.
	private PrefsData()
	{
	}

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		stream.writeObject(new Opcode_SetPrefsKey(INT_VIDEO_EDGE, Integer.valueOf(this.videoEdgePixelMax)));
		stream.writeObject(new Opcode_SetPrefsKey(LONG_FOLLOW_CACHE_BYTES, Long.valueOf(this.followCacheTargetBytes)));
		stream.writeObject(new Opcode_SetPrefsKey(LONG_REPUBLISH_INTERVAL_MILLIS, Long.valueOf(this.republishIntervalMillis)));
		stream.writeObject(new Opcode_SetPrefsKey(LONG_FOLLOWEE_REFRESH_MILLIS, Long.valueOf(this.followeeRefreshMillis)));
	}
}
