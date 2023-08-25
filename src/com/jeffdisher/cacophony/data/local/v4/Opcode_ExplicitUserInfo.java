package com.jeffdisher.cacophony.data.local.v4;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This opcode represents a top-level user reference in the explicit cache.
 * This opcode represents a reference to pinning the following pieces of data:
 * -StreamIndex (indexCid)
 * -StreamRecommendations (recommendationsCid)
 * -StreamRecords (recordsCid)
 * -StreamDescription (descriptionCid)
 * -user pic (userPicCid)
 * Only the userPicCid can be null.  NOTE:  Until we complete the transition to V4 data model, recordsCid can also be
 * null.
 * The sizes and locations of all of these elements can be determined when the opcode is loaded by reading the local
 * node since they are known to be pinned there.
 * NOTE:  Even though all of this data can be derived from the indexCid, the other elements are included to simplify
 * startup.
 */
public record Opcode_ExplicitUserInfo(IpfsKey publicKey
		, long lastFetchAttemptMillis
		, long lastFetchSuccessMillis
		, IpfsFile indexCid
		, IpfsFile recommendationsCid
		, IpfsFile recordsCid
		, IpfsFile descriptionCid
		, IpfsFile userPicCid
		, long combinedSizeBytes
) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.EXPLICIT_USER_INFO;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsKey publicKey = deserializer.readKey();
			long lastFetchAttemptMillis = deserializer.readLong();
			long lastFetchSuccessMillis = deserializer.readLong();
			IpfsFile indexCid = deserializer.readCid();
			IpfsFile recommendationsCid = deserializer.readCid();
			IpfsFile recordsCid = deserializer.readCid();
			IpfsFile descriptionCid = deserializer.readCid();
			IpfsFile userPicCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_ExplicitUserInfo(publicKey
					, lastFetchAttemptMillis
					, lastFetchSuccessMillis
					, indexCid
					, recommendationsCid
					, recordsCid
					, descriptionCid
					, userPicCid
					, combinedSizeBytes
			);
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
		// TODO:  Make sure that this records CID is added and pinned in the last part of the format change.
		// TODO:  Also add lastFetchSuccessMillis.
		context.explicitCache().addUserInfo(this.publicKey
				, this.lastFetchAttemptMillis
				, indexCid
				, recommendationsCid
				, descriptionCid
				, userPicCid
				, combinedSizeBytes
		);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		serializer.writeKey(this.publicKey);
		serializer.writeLong(this.lastFetchAttemptMillis);
		serializer.writeLong(this.lastFetchSuccessMillis);
		serializer.writeCid(this.indexCid);
		serializer.writeCid(this.recommendationsCid);
		serializer.writeCid(this.recordsCid);
		serializer.writeCid(this.descriptionCid);
		serializer.writeCid(this.userPicCid);
		serializer.writeLong(this.combinedSizeBytes);
	}
}
