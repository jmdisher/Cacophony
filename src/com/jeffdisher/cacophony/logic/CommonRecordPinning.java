package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.ProtocolDataException;


/**
 * Just a container of common record loading helpers.
 */
public class CommonRecordPinning
{
	/**
	 * The common helper for loading and pinning a record.
	 * NOTE:  The caller is responsible for committing or rolling back the transaction, whether it returns or throws.
	 * 
	 * @param transaction The transaction for accessing the network (caller must commit or roll back).
	 * @param videoEdgePixelMax The preferred edge size of the video to pin.
	 * @param recordCid The CID of the record.
	 * @param shouldPinLeaves True if we should pin the leaves also or false to only pin the meta-data.
	 * @return The info for this StreamRecord (never null).
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static CachedRecordInfo loadAndPinRecord(ConcurrentTransaction transaction, int videoEdgePixelMax, IpfsFile recordCid, boolean shouldPinLeaves) throws ProtocolDataException, IpfsConnectionException
	{
		// Find and populate the cache.
		AbstractRecord record = transaction.loadNotCached(recordCid, "record", AbstractRecord.SIZE_LIMIT_BYTES, AbstractRecord.DESERIALIZER).get();
		LeafFinder leafFinder = LeafFinder.parseRecord(record);
		IpfsFile thumbnailCid = leafFinder.thumbnail;
		LeafFinder.VideoLeaf videoLeaf = leafFinder.largestVideoWithLimit(videoEdgePixelMax);
		IpfsFile videoCid = (null != videoLeaf) ? videoLeaf.cid() : null;
		IpfsFile audioCid = (null != videoLeaf) ? null : leafFinder.audio;
		
		// Now that we can see what leaves we want, see if we should pin them or record that there is other data to cache, later.
		boolean hasDataToCache = false;
		if (!shouldPinLeaves)
		{
			if (null != thumbnailCid)
			{
				hasDataToCache = true;
				thumbnailCid = null;
			}
			if (null != videoCid)
			{
				hasDataToCache = true;
				videoCid = null;
			}
			if (null != audioCid)
			{
				hasDataToCache = true;
				audioCid = null;
			}
		}
		
		// Now, pin everything and update the cache.
		FuturePin pinRecord = transaction.pin(recordCid);
		FuturePin pinThumbnail = (null != thumbnailCid)
				? transaction.pin(thumbnailCid)
				: null
		;
		FuturePin pinVideo = (null != videoCid)
				? transaction.pin(videoCid)
				: null
		;
		FuturePin pinAudio = (null != audioCid)
				? transaction.pin(audioCid)
				: null
		;
		
		// Since we are relying on the transaction, the caller can rollback if we fail so we don't need to handle specialized revert, here.
		pinRecord.get();
		long combinedSizeBytes = transaction.getSizeInBytes(recordCid).get();
		if (null != pinThumbnail)
		{
			pinThumbnail.get();
			combinedSizeBytes += transaction.getSizeInBytes(thumbnailCid).get();
		}
		if (null != pinVideo)
		{
			pinVideo.get();
			combinedSizeBytes += transaction.getSizeInBytes(videoCid).get();
		}
		if (null != pinAudio)
		{
			pinAudio.get();
			combinedSizeBytes += transaction.getSizeInBytes(audioCid).get();
		}
		return new CachedRecordInfo(recordCid, hasDataToCache, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
	}
}
