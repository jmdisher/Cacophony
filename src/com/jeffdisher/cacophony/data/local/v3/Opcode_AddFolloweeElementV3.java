package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v4.OpcodeDeserializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeSerializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeType;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The opcode to describe a single cached element of a followee.
 * Note that, while we pin all StreamRecord meta-data elements, we only create one of these instances for the case where
 * we also pinned some of the sub-elements.
 */
public record Opcode_AddFolloweeElementV3(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.DEPRECATED_V3_ADD_FOLLOWEE_ELEMENT;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsKey followeeKey = deserializer.readKey();
			IpfsFile elementHash = deserializer.readCid();
			IpfsFile imageHash = deserializer.readCid();
			IpfsFile leafHash = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_AddFolloweeElementV3(followeeKey, elementHash, imageHash, leafHash, combinedSizeBytes);
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
		// Note that V3 allows FollowingCacheElement without image or leaf but V4 doesn't.
		if ((null != this.imageHash) || (null != this.leafHash))
		{
			context.followees().addElement(this.followeeKey
					, new FollowingCacheElement(this.elementHash, this.imageHash, this.leafHash, this.combinedSizeBytes)
			);
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
		serializer.writeKey(this.followeeKey);
		serializer.writeCid(this.elementHash);
		serializer.writeCid(this.imageHash);
		serializer.writeCid(this.leafHash);
		serializer.writeLong(this.combinedSizeBytes);
	}
}
