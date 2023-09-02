package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v4.OpcodeDeserializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeSerializer;
import com.jeffdisher.cacophony.data.local.v4.OpcodeType;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This opcode represents data associated with a single stream record in the explicit cache.
 * This opcode represents a reference to pinning the following pieces of data:
 * -StreamRecord (streamCid)
 * -the thumbnail (if there is one - thumbnailCid)
 * -the leaf data (NEVER both of these):
 *  -video (videoCid)
 *  -audio (audioCid)
 * The sizes and locations of all of these elements can be determined when the opcode is loaded by reading the local
 * node since they are known to be pinned there.
 * NOTE:  If the StreamRecord has a valid thumbnail and/or leaf, they WILL be pinned (this cache element is considered a
 * complete video of this data and CANNOT be a partial representation).
 */
public record Opcode_ExplicitStreamRecordV3(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.DEPRECATED_V3_EXPLICIT_STREAM_RECORD;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile streamCid = deserializer.readCid();
			IpfsFile thumbnailCid = deserializer.readCid();
			IpfsFile videoCid = deserializer.readCid();
			IpfsFile audioCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_ExplicitStreamRecordV3(streamCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
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
		commonApply(context.explicitCache());
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
		serializer.writeCid(this.streamCid);
		serializer.writeCid(this.thumbnailCid);
		serializer.writeCid(this.videoCid);
		serializer.writeCid(this.audioCid);
		serializer.writeLong(this.combinedSizeBytes);
	}


	private void commonApply(ExplicitCacheData explicitCache)
	{
		// We know that the V3 version of this opcode reflected entires which were always fully-cached.
		boolean hasDataToCache = false;
		CachedRecordInfo info = new CachedRecordInfo(this.streamCid, hasDataToCache, this.thumbnailCid, this.videoCid, this.audioCid, this.combinedSizeBytes);
		explicitCache.addStreamRecord(this.streamCid, info);
	}
}
