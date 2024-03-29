package com.jeffdisher.cacophony.data.local.v4;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;


/**
 * The container of the system's data objects which need to be populated when decoding an opcode stream.
 */
public record OpcodeContext(ChannelData channelData
		, PrefsData prefs
		, FolloweeLoader followeeLoader
		, FavouritesCacheData favouritesCache
		, ExplicitCacheData explicitCache
)
{
}
