package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The opcode to describe a single cached element of a followee.
 * Note that, while we pin all StreamRecord meta-data elements, we only create one of these instances for the case where
 * we also pinned some of the sub-elements.
 */
public record Opcode_AddFolloweeElement(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.ADD_FOLLOWEE_ELEMENT;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsKey followeeKey = deserializer.readKey();
			IpfsFile elementHash = deserializer.readCid();
			IpfsFile imageHash = deserializer.readCid();
			IpfsFile leafHash = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_AddFolloweeElement(followeeKey, elementHash, imageHash, leafHash, combinedSizeBytes);
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
		context.followees().addElement(this.followeeKey
				, new FollowingCacheElement(this.elementHash, this.imageHash, this.leafHash, this.combinedSizeBytes)
		);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		serializer.writeKey(this.followeeKey);
		serializer.writeCid(this.elementHash);
		serializer.writeCid(this.imageHash);
		serializer.writeCid(this.leafHash);
		serializer.writeLong(this.combinedSizeBytes);
	}
}