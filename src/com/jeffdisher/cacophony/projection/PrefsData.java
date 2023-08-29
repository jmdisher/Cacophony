package com.jeffdisher.cacophony.projection;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SetPrefsInt;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SetPrefsLong;


/**
 * A high-level projection of the prefs data which also exposes an interface to serialize itself into data opcodes.
 */
public class PrefsData
{
	public static final String INT_VIDEO_EDGE = "INT_VIDEO_EDGE";
	public static final String LONG_REPUBLISH_INTERVAL_MILLIS = "LONG_REPUBLISH_INTERVAL_MILLIS";

	public static final String LONG_EXPLICIT_CACHE_BYTES = "LONG_EXPLICIT_CACHE_BYTES";

	public static final String LONG_FOLLOW_CACHE_BYTES = "LONG_FOLLOW_CACHE_BYTES";
	public static final String LONG_FOLLOWEE_REFRESH_MILLIS = "LONG_FOLLOWEE_REFRESH_MILLIS";
	public static final String LONG_FOLLOWEE_THUMBNAIL_BYTES = "LONG_FOLLOWEE_THUMBNAIL_BYTES";
	public static final String LONG_FOLLOWEE_AUDIO_BYTES = "LONG_FOLLOWEE_AUDIO_BYTES";
	public static final String LONG_FOLLOWEE_VIDEO_BYTES = "LONG_FOLLOWEE_VIDEO_BYTES";

	// We will default to 720p, which is 720/1280, so we use 1280 as the edge size.
	public static final int DEFAULT_VIDEO_EDGE = 1280;
	// The public key publications seem to be valid for about 24 hours, by default, so we will use 12.
	public static final long DEFAULT_REPUBLISH_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L;

	// We will use 1 GB as the size of the explicit cache, since we usually satisfy requests from the local user or
	// followee cache so this is typically just used for one-offs.
	public static final long DEFAULT_EXPLICIT_CACHE_BYTES = 1_000_000_000L;

	// We will start with a followee cache default target size of 10 GB (probably too small but not ultra-tiny).
	// (this is mutable since we override it in some tests).
	public static final long DEFAULT_FOLLOWEE_CACHE_BYTES = 10_000_000_000L;
	// We will default to refreshing each followeee once per hour.
	public static final long DEFAULT_FOLLOWEE_REFRESH_MILLIS = 60L * 60L * 1000L;
	// The default sizes for followee element cache limits aren't based on hard science, just upper bounds over what seems reasonable, based on other defaults.
	public static final long DEFAULT_FOLLOWEE_THUMBNAIL_BYTES = 10_000_000L;
	public static final long DEFAULT_FOLLOWEE_AUDIO_BYTES = 200_000_000L;
	public static final long DEFAULT_FOLLOWEE_VIDEO_BYTES = 2_000_000_000L;


	public static PrefsData defaultPrefs()
	{
		PrefsData prefs = new PrefsData();
		
		prefs.videoEdgePixelMax = DEFAULT_VIDEO_EDGE;
		prefs.republishIntervalMillis = DEFAULT_REPUBLISH_INTERVAL_MILLIS;
		
		prefs.explicitCacheTargetBytes = DEFAULT_EXPLICIT_CACHE_BYTES;
		
		prefs.followeeCacheTargetBytes = DEFAULT_FOLLOWEE_CACHE_BYTES;
		prefs.followeeRefreshMillis = DEFAULT_FOLLOWEE_REFRESH_MILLIS;
		prefs.followeeRecordThumbnailMaxBytes = DEFAULT_FOLLOWEE_THUMBNAIL_BYTES;
		prefs.followeeRecordAudioMaxBytes = DEFAULT_FOLLOWEE_AUDIO_BYTES;
		prefs.followeeRecordVideoMaxBytes = DEFAULT_FOLLOWEE_VIDEO_BYTES;
		
		return prefs;
	}


	// These are exposed just as public fields since this is effectively a mutable struct.
	/**
	 * The maximum number of pixels on an edge of a video.  For example, 1280 would should a 720p video in landscape or
	 * portrait.
	 */
	public int videoEdgePixelMax;

	/**
	 * The number of milliseconds between home user root republication attempts.  This is required since the IPNS record
	 * only lasts for 24 hours.  This means that the value should be something less than 24 hours.
	 */
	public long republishIntervalMillis;

	/**
	 * The maximum number of bytes we will try to keep the explicit cache under.  If the cache grows above this size, it
	 * will purge least recently used entries until it is under this limit.
	 */
	public long explicitCacheTargetBytes;

	/**
	 * The number of bytes of content the followee cache will target.  Note that it can sometimes go above this value,
	 * and doesn't count meta-data size (only leaf element size), but will generally try to target this size
	 */
	public long followeeCacheTargetBytes;
	/**
	 * The number of milliseconds between polling attempts of existing followees for new content.  The polling attempt
	 * will reset the refresh timer whether it is a success or not.
	 */
	public long followeeRefreshMillis;
	/**
	 * The maximum size, in bytes, of a followee post thumbnail which will be considered for entry in the followee
	 * cache.
	 */
	public long followeeRecordThumbnailMaxBytes;
	/**
	 * The maximum size, in bytes, of a followee post audio attachment which will be considered for entry in the
	 * followee cache.
	 */
	public long followeeRecordAudioMaxBytes;
	/**
	 * The maximum size, in bytes, of a followee post video attachment which will be considered for entry in the
	 * followee cache.
	 */
	public long followeeRecordVideoMaxBytes;

	// We keep this private just so the factory is used to explicitly create the defaults.
	private PrefsData()
	{
	}

	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		writer.writeOpcode(new Opcode_SetPrefsInt(INT_VIDEO_EDGE, Integer.valueOf(this.videoEdgePixelMax)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_REPUBLISH_INTERVAL_MILLIS, Long.valueOf(this.republishIntervalMillis)));
		
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_EXPLICIT_CACHE_BYTES, Long.valueOf(this.explicitCacheTargetBytes)));
		
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOW_CACHE_BYTES, Long.valueOf(this.followeeCacheTargetBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_REFRESH_MILLIS, Long.valueOf(this.followeeRefreshMillis)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_THUMBNAIL_BYTES, Long.valueOf(this.followeeRecordThumbnailMaxBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_AUDIO_BYTES, Long.valueOf(this.followeeRecordAudioMaxBytes)));
		writer.writeOpcode(new Opcode_SetPrefsLong(LONG_FOLLOWEE_VIDEO_BYTES, Long.valueOf(this.followeeRecordVideoMaxBytes)));
	}
}
