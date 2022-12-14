package com.jeffdisher.cacophony.data.local.v2;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * Opcode decoding interface related to the followee cache.  This is split out into its own interface mostly so that the
 * names and related callbacks are more obviously connected (since there are several methods related to the same
 * high-level relationship).
 */
public interface IFolloweeDecoding
{
	void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis);

	void addElement(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes);
}
