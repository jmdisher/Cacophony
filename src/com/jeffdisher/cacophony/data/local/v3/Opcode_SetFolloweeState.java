package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to set the state of a followee's channel.  Note that the individual cached elements are described by
 * Opcode_AddFolloweeElement.
 * Note that neither followeeKey nor indexRoot can be null but lastPollMillis will be 0L before the first full refresh.
 */
public record Opcode_SetFolloweeState(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.SET_FOLLOWEE_STATE;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsKey followeeKey = deserializer.readKey();
			IpfsFile indexRoot = deserializer.readCid();
			long lastPollMillis = deserializer.readLong();
			return new Opcode_SetFolloweeState(followeeKey, indexRoot, lastPollMillis);
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
		context.followees().createNewFollowee(this.followeeKey, this.indexRoot, this.lastPollMillis);
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
}
