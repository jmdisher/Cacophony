package com.jeffdisher.cacophony.data.local.v4;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This opcode describes a followee record which failed to be loaded.
 * That is, the meta-data itself failed to be loaded.  The opcode describes whether the problem is permanent (corrupt
 * data) or not (timeout).
 */
public record Opcode_SkipFolloweeRecord(IpfsFile recordCid, boolean isPermanent) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.SKIP_FOLLOWEE_RECORD;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile recordCid = deserializer.readCid();
			boolean isPermanent = deserializer.readBoolean();
			return new Opcode_SkipFolloweeRecord(recordCid, isPermanent);
		};
	}


	@Override
	public OpcodeType type()
	{
		return TYPE;
	}

	@Override
	public void applyV3(OpcodeContextV3 context)
	{
		// This opcode does NOT appear in V3 data streams.
		throw Assert.unreachable();
	}

	@Override
	public void apply(OpcodeContext context)
	{
		context.followeeLoader().skipFolloweeRecord(this.recordCid, this.isPermanent);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		Assert.assertTrue(null != this.recordCid);
		
		serializer.writeCid(this.recordCid);
		serializer.writeBoolean(this.isPermanent);
	}
}
