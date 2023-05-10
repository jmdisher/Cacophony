package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This opcode represents a top-level user reference in the explicit cache.
 * This opcode represents a reference to pinning the following pieces of data:
 * -StreamIndex (indexCid)
 * -StreamRecommendations (recommendationsCid)
 * -StreamDescription (descriptionCid)
 * -user pic (userPicCid)
 * The sizes and locations of all of these elements can be determined when the opcode is loaded by reading the local
 * node since they are known to be pinned there.
 * NOTE:  Even though all of this data can be derived from the indexCid, the other elements are included to simplify
 * startup.
 */
public record Opcode_ExplicitUserInfo(IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.EXPLICIT_USER_INFO;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile indexCid = deserializer.readCid();
			IpfsFile recommendationsCid = deserializer.readCid();
			IpfsFile descriptionCid = deserializer.readCid();
			IpfsFile userPicCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_ExplicitUserInfo(indexCid, recommendationsCid, descriptionCid, userPicCid, combinedSizeBytes);
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
		context.explicitCache().addUserInfo(this.indexCid, this.recommendationsCid, this.descriptionCid, this.userPicCid, this.combinedSizeBytes);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		serializer.writeCid(this.indexCid);
		serializer.writeCid(this.recommendationsCid);
		serializer.writeCid(this.descriptionCid);
		serializer.writeCid(this.userPicCid);
		serializer.writeLong(this.combinedSizeBytes);
	}
}
