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
