package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * The abstract interface for LocalActions, provided to allow mocks for testing.
 */
public interface ILocalActions
{
	LocalIndex readIndex();

	void storeIndex(LocalIndex index);

	IConnection getSharedConnection() throws IpfsConnectionException;

	IPinMechanism getSharedPinMechanism() throws IpfsConnectionException;

	FollowIndex loadFollowIndex();

	GlobalPrefs readPrefs();

	void storePrefs(GlobalPrefs prefs);

	GlobalPinCache loadGlobalPinCache();

	/**
	 * This is purely to improve error reporting - returns the full path to the configuration directory.
	 * 
	 * @return The full path to the configuration directory.
	 */
	String getConfigDirectoryFullPath();
}
