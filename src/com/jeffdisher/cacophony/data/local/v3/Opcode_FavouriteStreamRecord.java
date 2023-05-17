package com.jeffdisher.cacophony.data.local.v3;

import java.util.function.Function;

import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.types.IpfsFile;


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
public record Opcode_FavouriteStreamRecord(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes) implements IDataOpcode
{
	public static final OpcodeType TYPE = OpcodeType.FAVOURITE_STREAM_RECORD;

	public static void register(Function<OpcodeDeserializer, IDataOpcode>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (OpcodeDeserializer deserializer) -> {
			IpfsFile streamCid = deserializer.readCid();
			IpfsFile thumbnailCid = deserializer.readCid();
			IpfsFile videoCid = deserializer.readCid();
			IpfsFile audioCid = deserializer.readCid();
			long combinedSizeBytes = deserializer.readLong();
			return new Opcode_FavouriteStreamRecord(streamCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
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
		CachedRecordInfo info = new CachedRecordInfo(this.streamCid, this.thumbnailCid, this.videoCid, this.audioCid, this.combinedSizeBytes);
		context.favouritesCache().addStreamRecord(this.streamCid, info);
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
}
