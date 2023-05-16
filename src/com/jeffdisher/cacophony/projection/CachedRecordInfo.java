package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.types.IpfsFile;


public record CachedRecordInfo(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes)
{
}
