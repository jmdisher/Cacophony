package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The interface provided when running commands, allowing them to access logging facilities and create/load
 * configurations to actually interact with the system.
 */
public interface IEnvironment
{
	/**
	 * The interface for the logging context of a specific operation.
	 * This allows high-level logging or activities which can be considered logically related.
	 */
	public static interface IOperationLog
	{
		/**
		 * Starts a nested logging context.
		 * 
		 * @param openingMessage The opening message to log.
		 * @return The logging context for a nested operation.
		 */
		IOperationLog logStart(String openingMessage);
		/**
		 * Logs a specific part of the operation.
		 * 
		 * @param message The message.
		 */
		void logOperation(String message);
		/**
		 * Closes the logging context at the end of the operation.
		 * 
		 * @param finishMessage The final message to close the log.
		 */
		void logFinish(String finishMessage);
		/**
		 * Log an operation-related message as verbose, meaning some implementations or configurations may suppress it.
		 * 
		 * @param message The message.
		 */
		void logVerbose(String message);
	}

	/**
	 * Log a message as verbose, meaning some implementations or configurations may suppress it.
	 * 
	 * @param message The message.
	 */
	void logVerbose(String message);

	/**
	 * Starts a new logging context.
	 * 
	 * @param openingMessage The opening message to log.
	 * @return The logging context for an operation.
	 */
	IOperationLog logStart(String openingMessage);

	/**
	 * Logs an error message and sets that an error has occurred, in the environment state.
	 * 
	 * @param message The message to log.
	 */
	void logError(String message);

	/**
	 * Returns the shared scheduler for network operations.
	 * 
	 * @return The shared network scheduler instance.
	 */
	INetworkScheduler getSharedScheduler();

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
	 * @return The lower-level IPFS connection object (getSharedScheduler() is usually more appropriate).
	 */
	IConnection getConnection();

	/**
	 * @return The name used to identify the signing key for publication on the local IPFS node (usually "Cacophony").
	 * This value is never null.
	 */
	String getKeyName();

	/**
	 * @return The public key of this user.  This value is never null.
	 */
	IpfsKey getPublicKey();

	/**
	 * @return Milliseconds since the Unix Epoch.
	 */
	long currentTimeMillis();
}
