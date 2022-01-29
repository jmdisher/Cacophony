package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;


/**
 * The abstract interface for LocalActions, provided to allow mocks for testing.
 */
public interface ILocalActions
{
	LocalIndex readIndex();

	void storeIndex(LocalIndex index);

	IConnection getSharedConnection();

	IPinMechanism getSharedPinMechanism();

	FollowIndex loadFollowIndex();

	GlobalPrefs readPrefs();

	GlobalPinCache loadGlobalPinCache();
}
