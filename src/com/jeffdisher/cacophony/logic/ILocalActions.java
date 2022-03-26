package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * The abstract interface for LocalActions, provided to allow mocks for testing.
 */
public interface ILocalActions
{
	/**
	 * Creates a new index, setting it as the shared instance.  Does NOT write to disk.
	 * 
	 * @param ipfsConnectionString The IPFS connection string we will use for our connections.
	 * @param keyName The name of the IPFS key to use when publishing root elements.
	 * @return The shared index.
	 * @throws UsageException If there is already a loaded shared index or already one on disk.
	 */
	LocalIndex createEmptyIndex(String ipfsConnectionString, String keyName) throws UsageException;

	/**
	 * @return The shared LocalIndex instance, lazily loading it if needed.
	 * @throws UsageException If there is no existing shared index on disk.
	 */
	LocalIndex readExistingSharedIndex() throws UsageException;

	/**
	 * Sets the given localIndex as the new shared index and writes it to disk.
	 * 
	 * @param localIndex The new local index.
	 */
	void storeSharedIndex(LocalIndex localIndex);

	IConnection getSharedConnection() throws IpfsConnectionException;

	FollowIndex loadFollowIndex();

	GlobalPrefs readSharedPrefs();

	void storeSharedPrefs(GlobalPrefs prefs);

	GlobalPinCache loadGlobalPinCache();

	/**
	 * This is purely to improve error reporting - returns the full path to the configuration directory.
	 * 
	 * @return The full path to the configuration directory.
	 */
	String getConfigDirectoryFullPath();
}
