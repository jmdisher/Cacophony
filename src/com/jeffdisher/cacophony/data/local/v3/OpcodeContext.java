package com.jeffdisher.cacophony.data.local.v3;

import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.PrefsData;


/**
 * The container of the system's data objects which need to be populated when decoding an opcode stream.
 */
public record OpcodeContext(ChannelData channelData, PrefsData prefs, FolloweeData followees)
{
}
