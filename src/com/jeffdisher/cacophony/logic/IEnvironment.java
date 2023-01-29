package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


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
	 * Returns the shared scheduler for network operations, lazily creating it if it doesn't yet exist.
	 * Note that the scheduler is lazily created but long-lived and shared by potentially multiple threads and
	 * concurrent commands.
	 * 
	 * @param ipfs The IPFS connection to use if the scheduler is being lazily constructed.
	 * @param keyName The channel's key name to use if the scheduler is being lazily constructed.
	 * @return The shared network scheduler instance.
	 * @throws IpfsConnectionException If there was an error creating the scheduler.
	 */
	INetworkScheduler getSharedScheduler(IConnection ipfs, String keyName) throws IpfsConnectionException;

	/**
	 * Returns a shared DraftManager instance, lazily creating it if needed, on top of the environment's filesystem's
	 * draft directory.
	 * NOTE:  The DraftManager is NOT protected by the same locks as defined in the "access" design as it is backed by
	 * data which is completely unrelated to the core channel meta-data.
	 * 
	 * @return The shared DraftManager instance.
	 */
	DraftManager getSharedDraftManager();

	/**
	 * @return The shared LocalDataModel to allow safe access to the configured filesystem.
	 */
	LocalDataModel getSharedDataModel();

	/**
	 * @return The config filesystem location.
	 */
	IConfigFileSystem getConfigFileSystem();

	/**
	 * @return The factory for creating new connections.
	 */
	IConnectionFactory getConnectionFactory();

	/**
	 * @return Milliseconds since the Unix Epoch.
	 */
	long currentTimeMillis();
}
