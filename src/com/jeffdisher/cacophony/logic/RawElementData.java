package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This is just a mutable struct (cannot be meaningfully hashed) which is used by the algorithms which decide what to
 * cache locally, just to keep this information in one place while it is being actively used.
 * We only store the futures temporarily, both to make the current "phase" of use more obvious but also to avoid
 * exception errors which wouldn't be able to happen.
 */
public class RawElementData
{
	public IpfsFile elementCid;
	public FutureSize futureSize;
	public long size;
	public FuturePin futureElementPin;
	public FutureRead<StreamRecord> futureRecord;
	public StreamRecord record;
	
	public IpfsFile thumbnailHash;
	public IpfsFile videoHash;
	public FutureSize thumbnailSizeFuture;
	public FutureSize videoSizeFuture;
	public long thumbnailSize;
	public long videoSize;
	public FuturePin futureThumbnailPin;
	public FuturePin futureVideoPin;
}
