package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to describe the state of a channel.  While the keyName and publicKey won't change, once created, the
 * latestRoot will change whenever the the channel content is updated.  The entire opcode is written as a snapshot of
 * the channel root state and no fields can be null.
 */
public record Opcode_DefineChannel(String keyName, IpfsKey publicKey, IpfsFile latestRoot) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.DEFINE_CHANNEL;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			String keyName = deserializer.readString();
			IpfsKey publicKey = deserializer.readKey();
			IpfsFile latestRoot = deserializer.readCid();
			return new Opcode_DefineChannel(keyName, publicKey, latestRoot);
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
		context.channelData().initializeChannelState(this.keyName, this.publicKey, this.latestRoot);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		// We don't let anything here be null.
		Assert.assertTrue(null != this.keyName);
		Assert.assertTrue(null != this.publicKey);
		Assert.assertTrue(null != this.latestRoot);
		
		serializer.writeString(this.keyName);
		serializer.writeKey(this.publicKey);
		serializer.writeCid(this.latestRoot);
	}
}
