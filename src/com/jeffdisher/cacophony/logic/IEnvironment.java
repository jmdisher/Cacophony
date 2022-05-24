package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;


/**
 * The interface provided when running commands, allowing them to access logging facilities and create/load
 * configurations to actually interact with the system.
 */
public interface IEnvironment
{
	/**
	 * This interface is just used to allow higher-level operation logging.
	 */
	public static interface IOperationLog
	{
		void finish(String finishMessage);
	}

	void logToConsole(String message);
	IOperationLog logOperation(String openingMessage);

	/**
	 * Logs an error message and sets that an error has occurred, in the environment state.
	 * 
	 * @param message The message to log.
	 */
	void logError(String message);

	/**
	 * Called when a config needs to be created for the first time.
	 * 
	 * @return The config object, with initial/empty data.
	 * @throws UsageException If the config directory already existed or couldn't be created.
	 */
	LocalConfig createNewConfig(String ipfsConnectionString, String keyName) throws UsageException;

	/**
	 * Called when an existing config should be loaded from disk.
	 * 
	 * @return The config object.
	 * @throws UsageException If the config directory is missing or b
	 * @throws VersionException The version file is missing or an unknown version.
	 */
	LocalConfig loadExistingConfig() throws UsageException, VersionException;

	/**
	 * Used in some testing modes to enable additional verifications that sizes/publications/etc are consistent.  Should
	 * not normally be used as it adds some additional network reads into the critical path.
	 * 
	 * @return True if additional checks should be applied.
	 */
	boolean shouldEnableVerifications();
}
