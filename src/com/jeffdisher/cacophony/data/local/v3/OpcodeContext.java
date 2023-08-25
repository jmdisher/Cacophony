package com.jeffdisher.cacophony.data.local.v3;

import java.util.List;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The container of the system's data objects which need to be populated when decoding an opcode stream.
 */
public record OpcodeContext(ChannelData channelData
		, PrefsData prefs
		, FolloweeData followees
		, FavouritesCacheData favouritesCache
		, ExplicitCacheData explicitCache
		, List<IpfsFile> unpinsToRationalize
)
{
}
