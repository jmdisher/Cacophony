package com.jeffdisher.cacophony.data.local.v4;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This opcode represents data associated with a single stream record in the explicit cache.
 * This opcode represents a reference to pinning the following pieces of data:
 * -StreamRecord (streamCid)
 * -hasDataToCache is true if there are leaves associated with streamCid which are normally cached, but are not
 * -the thumbnail (if there is one - thumbnailCid)
 * -the leaf data (NEVER both of these):
 *  -video (videoCid)
 *  -audio (audioCid)
 * The sizes and locations of all of these elements can be determined when the opcode is loaded by reading the local
 * node since they are known to be pinned there.
 * NOTE:  If the StreamRecord has a valid thumbnail and/or leaf, they WILL be pinned (this cache element is considered a
 * complete video of this data and CANNOT be a partial representation).
 */
public record Opcode_ExplicitStreamRecord(IpfsFile streamCid, boolean hasDataToCache, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.EXPLICIT_STREAM_RECORD;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile streamCid = deserializer.readCid();
			boolean hasDataToCache = deserializer.readBoolean();
			IpfsFile thumbnailCid = deserializer.readCid();
			IpfsFile videoCid = deserializer.readCid();
			IpfsFile audioCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_ExplicitStreamRecord(streamCid, hasDataToCache, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
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
		commonApply(context.explicitCache());
	}

	@Override
	public void write(OpcodeSerializer serializer)
	{
		serializer.writeCid(this.streamCid);
		serializer.writeBoolean(this.hasDataToCache);
		serializer.writeCid(this.thumbnailCid);
		serializer.writeCid(this.videoCid);
		serializer.writeCid(this.audioCid);
		serializer.writeLong(this.combinedSizeBytes);
	}


	private void commonApply(ExplicitCacheData explicitCache)
	{
		CachedRecordInfo info = new CachedRecordInfo(this.streamCid, this.hasDataToCache, this.thumbnailCid, this.videoCid, this.audioCid, this.combinedSizeBytes);
		explicitCache.addStreamRecord(this.streamCid, info);
	}
}
