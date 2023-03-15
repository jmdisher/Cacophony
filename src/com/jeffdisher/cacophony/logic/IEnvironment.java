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
	 * @return The IPFS API server connect string (usually "/ip4/127.0.0.1/tcp/5001").
	 */
	String getIpfsConnectString();

	/**
	 * @return The name used to identify the signing key for publication on the local IPFS node (usually "Cacophony").
	 * Value is null if the command doesn't require a key.
	 */
	String getKeyName();

	/**
	 * @return The public key of this user (null if the key isn't required for this command).
	 */
	IpfsKey getPublicKey();

	/**
	 * @return Milliseconds since the Unix Epoch.
	 */
	long currentTimeMillis();
}
