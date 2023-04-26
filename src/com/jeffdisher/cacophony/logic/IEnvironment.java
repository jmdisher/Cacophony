package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;


/**
 * This interface contains the details of the environment where the program was started, meaning any of the resources
 * which were bootstrapped before the system started running.  For the most part, this just refers to shared resources.
 * The expectation is that a single instance will be created during start-up and passed through the rest of the system.
 */
public interface IEnvironment
{
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
	 * @return The lower-level IPFS connection object (getSharedScheduler() is usually more appropriate).
	 */
	IConnection getConnection();

	/**
	 * @return Milliseconds since the Unix Epoch.
	 */
	long currentTimeMillis();
}
