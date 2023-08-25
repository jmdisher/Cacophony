package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v4.OpcodeDeserializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeSerializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeType;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


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
public record Opcode_ExplicitUserInfoV3(IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.DEPRECATED_V3_EXPLICIT_USER_INFO;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile indexCid = deserializer.readCid();
			IpfsFile recommendationsCid = deserializer.readCid();
			IpfsFile descriptionCid = deserializer.readCid();
			IpfsFile userPicCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_ExplicitUserInfoV3(indexCid, recommendationsCid, descriptionCid, userPicCid, combinedSizeBytes);
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
		// NOTE:  We want to drop these CIDs since the ExplicitCacheData now needs more information for user info.
		context.unpinsToRationalize().add(this.indexCid);
		context.unpinsToRationalize().add(this.recommendationsCid);
		context.unpinsToRationalize().add(this.descriptionCid);
		if (null != this.userPicCid)
		{
			context.unpinsToRationalize().add(this.userPicCid);
		}
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
		serializer.writeCid(this.indexCid);
		serializer.writeCid(this.recommendationsCid);
		serializer.writeCid(this.descriptionCid);
		serializer.writeCid(this.userPicCid);
		serializer.writeLong(this.combinedSizeBytes);
	}
}
