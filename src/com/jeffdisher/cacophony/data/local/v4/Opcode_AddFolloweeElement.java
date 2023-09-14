package com.jeffdisher.cacophony.data.local.v4;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to describe a single cached element of a followee.
 * As of V4 data model, we now store one of these records for every followee element we have cached, at all, even if it
 * is only the elementHash for the meta-data.
 * However, in these cases, combinedLeafSizeBytes will be 0 since it only counts the combined sizes of the imageHash and
 * leafHash (since we don't allow the elementHash to be evicted from cache).
 */
public record Opcode_AddFolloweeElement(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedLeafSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.ADD_FOLLOWEE_ELEMENT;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile elementHash = deserializer.readCid();
			IpfsFile imageHash = deserializer.readCid();
			IpfsFile leafHash = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_AddFolloweeElement(elementHash, imageHash, leafHash, combinedSizeBytes);
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
		context.followeeLoader().addFolloweeElement(this.elementHash, this.imageHash, this.leafHash, this.combinedLeafSizeBytes);
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		serializer.writeCid(this.elementHash);
		serializer.writeCid(this.imageHash);
		serializer.writeCid(this.leafHash);
		serializer.writeLong(this.combinedLeafSizeBytes);
	}
}
