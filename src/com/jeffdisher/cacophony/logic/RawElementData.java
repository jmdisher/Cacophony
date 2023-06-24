package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.global.AbstractRecord;
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
	public FutureRead<AbstractRecord> futureRecord;
	public AbstractRecord record;
	
	public IpfsFile thumbnailHash;
	public IpfsFile leafHash;
	public FutureSize thumbnailSizeFuture;
	public FutureSize leafSizeFuture;
	public long thumbnailSizeBytes;
	public long leafSizeBytes;
	public FuturePin futureThumbnailPin;
	public FuturePin futureLeafPin;
	
	// We will only have one of these leaves set, and it is stored in leafHash, so these redundant cases are just for more detailed uses.
	public IpfsFile videoLeafHash;
	public int videoEdgeSize;
	public IpfsFile audioLeafHash;
}
