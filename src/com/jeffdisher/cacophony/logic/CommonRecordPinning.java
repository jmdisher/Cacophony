package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class CommonRecordPinning
{
	/**
	 * The common helper for loading and pinning a record.
	 * 
	 * @param access Write-access to local storage and the network.
	 * @param videoEdgePixelMax The preferred edge size of the video to pin.
	 * @param recordCid The CID of the record.
	 * @return The info for this StreamRecord (never null).
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static CachedRecordInfo loadAndPinRecord(IWritingAccess access, int videoEdgePixelMax, IpfsFile recordCid) throws ProtocolDataException, IpfsConnectionException
	{
		// Find and populate the cache.
		StreamRecord record = access.loadNotCached(recordCid, "explicit record", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] bytes) -> GlobalData.deserializeRecord(bytes)).get();
		LeafFinder leafFinder = LeafFinder.parseRecord(record);
		IpfsFile thumbnailCid = leafFinder.thumbnail;
		LeafFinder.VideoLeaf videoLeaf = leafFinder.largestVideoWithLimit(videoEdgePixelMax);
		IpfsFile videoCid = (null != videoLeaf) ? videoLeaf.cid() : null;
		IpfsFile audioCid = (null != videoLeaf) ? null : leafFinder.audio;
		
		// Now, pin everything and update the cache.
		FuturePin pinRecord = access.pin(recordCid);
		FuturePin pinThumbnail = (null != thumbnailCid)
				? access.pin(thumbnailCid)
				: null
		;
		FuturePin pinVideo = (null != videoCid)
				? access.pin(videoCid)
				: null
		;
		FuturePin pinAudio = (null != audioCid)
				? access.pin(audioCid)
				: null
		;
		// We want to revert all the pins if any of these fail.
		try
		{
			pinRecord.get();
			long combinedSizeBytes = access.getSizeInBytes(recordCid).get();
			if (null != pinThumbnail)
			{
				pinThumbnail.get();
				combinedSizeBytes += access.getSizeInBytes(thumbnailCid).get();
			}
			if (null != pinVideo)
			{
				pinVideo.get();
				combinedSizeBytes += access.getSizeInBytes(videoCid).get();
			}
			if (null != pinAudio)
			{
				pinAudio.get();
				combinedSizeBytes += access.getSizeInBytes(audioCid).get();
			}
			return new CachedRecordInfo(recordCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
		}
		catch (IpfsConnectionException e)
		{
			access.unpin(recordCid);
			if (null != thumbnailCid)
			{
				access.unpin(thumbnailCid);
			}
			if (null != videoCid)
			{
				access.unpin(videoCid);
			}
			if (null != audioCid)
			{
				access.unpin(audioCid);
			}
			throw e;
		}
	}
}
