package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to set a single long value in the prefs.
 */
public record Opcode_SetPrefsLong(String key, long value) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.SET_PREFS_FIELD_LONG;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			String key = deserializer.readString();
			long value = deserializer.readLong();
			return new Opcode_SetPrefsLong(key, value);
		};
	}


	@Override
	public OpcodeType type()
	{
		return TYPE;
	}

	@Override
	public void apply(OpcodeContext context)
	{
		PrefsData prefs = context.prefs();
		Assert.assertTrue(null != prefs);
		if (this.key.equals(PrefsData.LONG_FOLLOW_CACHE_BYTES))
		{
			prefs.followCacheTargetBytes = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_REPUBLISH_INTERVAL_MILLIS))
		{
			prefs.republishIntervalMillis = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_FOLLOWEE_REFRESH_MILLIS))
		{
			prefs.followeeRefreshMillis = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_EXPLICIT_CACHE_BYTES))
		{
			prefs.explicitCacheTargetBytes = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_FOLLOWEE_THUMBNAIL_BYTES))
		{
			prefs.followeeRecordThumbnailMaxBytes = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_FOLLOWEE_AUDIO_BYTES))
		{
			prefs.followeeRecordAudioMaxBytes = this.value;
		}
		else if (this.key.equals(PrefsData.LONG_FOLLOWEE_VIDEO_BYTES))
		{
			prefs.followeeRecordVideoMaxBytes = this.value;
		}
		else
		{
			throw Assert.unreachable();
		}
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		// The key obviously can't be null.
		Assert.assertTrue(null != this.key);
		
		serializer.writeString(this.key);
		serializer.writeLong(this.value);
	}
}
