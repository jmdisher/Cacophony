package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v4.OpcodeDeserializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeSerializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeType;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to set the state of a followee's channel.  Note that the individual cached elements are described by
 * Opcode_AddFolloweeElement.
 * Note that neither followeeKey nor indexRoot can be null but lastPollMillis will be 0L before the first full refresh.
 */
public record Opcode_SetFolloweeStateV3(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.DEPRECATED_V3_SET_FOLLOWEE_STATE;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsKey followeeKey = deserializer.readKey();
			IpfsFile indexRoot = deserializer.readCid();
			long lastPollMillis = deserializer.readLong();
			return new Opcode_SetFolloweeStateV3(followeeKey, indexRoot, lastPollMillis);
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
		commonApply(context.followees());
	}

	@Override
	public void apply(OpcodeContext context)
	{
		// This opcode does NOT appear in V4 data streams.
		throw Assert.unreachable();
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		// We expect that these objects to be non-null.
		Assert.assertTrue(null != this.followeeKey);
		Assert.assertTrue(null != this.indexRoot);
		
		serializer.writeKey(this.followeeKey);
		serializer.writeCid(this.indexRoot);
		serializer.writeLong(this.lastPollMillis);
	}


	private void commonApply(FolloweeData followees)
	{
		// V3 data doesn't have partially-loaded followees.
		followees.createNewFollowee(this.followeeKey, this.indexRoot, null, this.lastPollMillis);
	}
}
