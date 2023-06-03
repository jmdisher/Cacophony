package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v2.Opcode_SetPrefsKey;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.Opcode_SetPrefsInt;
import com.jeffdisher.cacophony.data.local.v3.Opcode_SetPrefsLong;


/**
 * A high-level projection of the prefs data which also exposes an interface to serialize itself into data opcodes.
 */
public class PrefsData
{
	public static final String INT_VIDEO_EDGE = "INT_VIDEO_EDGE";
	public static final String LONG_FOLLOW_CACHE_BYTES = "LONG_FOLLOW_CACHE_BYTES";
	public static final String LONG_REPUBLISH_INTERVAL_MILLIS = "LONG_REPUBLISH_INTERVAL_MILLIS";
	public static final String LONG_FOLLOWEE_REFRESH_MILLIS = "LONG_FOLLOWEE_REFRESH_MILLIS";
	public static final String LONG_EXPLICIT_CACHE_BYTES = "LONG_EXPLICIT_CACHE_BYTES";
	public static final String LONG_FOLLOWEE_THUMBNAIL_BYTES = "LONG_FOLLOWEE_THUMBNAIL_BYTES";
	public static final String LONG_FOLLOWEE_AUDIO_BYTES = "LONG_FOLLOWEE_AUDIO_BYTES";
	public static final String LONG_FOLLOWEE_VIDEO_BYTES = "LONG_FOLLOWEE_VIDEO_BYTES";

	// We will default to 720p, which is 720/1280, so we use 1280 as the edge size.
	public static final int DEFAULT_VIDEO_EDGE = 1280;
	// We will start with a follower cache default target size of 10 GB (probably too small but not ultra-tiny).
	// (this is mutable since we override it in some tests).
	public static long DEFAULT_FOLLOW_CACHE_BYTES = 10_000_000_000L;
	// The public key publications seem to be valid for about 24 hours, by default, so we will use 12.
	public static final long DEFAULT_REPUBLISH_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L;
	// We will default to refreshing each followeee once per hour.
	public static final long DEFAULT_FOLLOWEE_REFRESH_MILLIS = 60L * 60L * 1000L;
	// We will use 1 GB as the size of the explicit cache, since we usually satisfy requests from the local user or
	// followee cache so this is typically just used for one-offs.
	public static long DEFAULT_EXPLICIT_CACHE_BYTES = 1_000_000_000L;
	// The default sizes for followee element cache limits aren't based on hard science, just upper bounds over what seems reasonable, based on other defaults.
	public static long DEFAULT_FOLLOWEE_THUMBNAIL_BYTES = 10_000_000L;
	public static long DEFAULT_FOLLOWEE_AUDIO_BYTES = 200_000_000L;
	public static long DEFAULT_FOLLOWEE_VIDEO_BYTES = 2_000_000_000L;


	public static PrefsData defaultPrefs()
	{
		PrefsData prefs = new PrefsData();
		prefs.videoEdgePixelMax = DEFAULT_VIDEO_EDGE;
		prefs.followCacheTargetBytes = DEFAULT_FOLLOW_CACHE_BYTES;
		prefs.republishIntervalMillis = DEFAULT_REPUBLISH_INTERVAL_MILLIS;
		prefs.followeeRefreshMillis = DEFAULT_FOLLOWEE_REFRESH_MILLIS;
		prefs.explicitCacheTargetBytes = DEFAULT_EXPLICIT_CACHE_BYTES;
		prefs.followeeRecordThumbnailMaxBytes = DEFAULT_FOLLOWEE_THUMBNAIL_BYTES;
		prefs.followeeRecordAudioMaxBytes = DEFAULT_FOLLOWEE_AUDIO_BYTES;
		prefs.followeeRecordVideoMaxBytes = DEFAULT_FOLLOWEE_VIDEO_BYTES;
		return prefs;
	}


	// These are exposed just as public fields since this is effectively a mutable struct.
	public int videoEdgePixelMax;
	public long followCacheTargetBytes;
	public long republishIntervalMillis;
	public long followeeRefreshMillis;
	public long explicitCacheTargetBytes;
	public long followeeRecordThumbnailMaxBytes;
	public long followeeRecordAudioMaxBytes;
	public long followeeRecordVideoMaxBytes;

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
		// We don't write the new elements to the V2 storage.
	}

	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		writer.writeOpcode(new Opcode_SetPrefsInt(INT_VIDEO_EDGE, Integer.valueOf(this.videoEdgePixelMax)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOW_CACHE_BYTES, Long.valueOf(this.followCacheTargetBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_REPUBLISH_INTERVAL_MILLIS, Long.valueOf(this.republishIntervalMillis)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_REFRESH_MILLIS, Long.valueOf(this.followeeRefreshMillis)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_EXPLICIT_CACHE_BYTES, Long.valueOf(this.explicitCacheTargetBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_THUMBNAIL_BYTES, Long.valueOf(this.followeeRecordThumbnailMaxBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_AUDIO_BYTES, Long.valueOf(this.followeeRecordAudioMaxBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_VIDEO_BYTES, Long.valueOf(this.followeeRecordVideoMaxBytes)));
	}
}
