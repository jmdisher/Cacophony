package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The representation of cached StreamRecord instances in most miscellaneous caching cases.
 * Details:
 * -streamCid points to the StreamRecord and cannot be null
 * -thumbnailCid points to the special record thumbnail (can be null)
 * -videoCid points to the chosen video cached (can be null)
 * -audioCid points to the audio cached (can be null)
 * -at most, ONE of videoCid and audioCid can be set
 * -combinedSizeBytes includes the size of ALL of these elements:  streamCid, thumbnailCid, videoCid, and audioCid
 * NOTE:  This is NOT used by followee cache elements since it accounts for element size differently.  See
 * FollowingCacheElement for more information.
 */
public record CachedRecordInfo(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes)
{
}
