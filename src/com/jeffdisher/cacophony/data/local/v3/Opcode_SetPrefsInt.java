package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to set a single integer value in the prefs.
 */
public record Opcode_SetPrefsInt(String key, int value) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.SET_PREFS_FIELD_INT;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			String key = deserializer.readString();
			int value = deserializer.readInt();
			return new Opcode_SetPrefsInt(key, value);
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
		if (this.key.equals(PrefsData.INT_VIDEO_EDGE))
		{
			context.prefs().videoEdgePixelMax = this.value;
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
		serializer.writeInt(this.value);
	}
}
