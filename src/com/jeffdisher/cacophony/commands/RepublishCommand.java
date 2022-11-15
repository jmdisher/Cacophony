package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Since the IPNS publications are temporary (typically only living up to 24 hours), the index hash needs to be
 * periodically republished to the network.
 */
public record RepublishCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		
		// Read the data elements.
		LocalIndex localIndex = null;
		try (IReadOnlyLocalData localData = local.getSharedLocalData().openForRead())
		{
			localIndex = localData.readLocalIndex();
		}
		// Get the previously posted index hash.
		IpfsFile indexHash = localIndex.lastPublishedIndex();
		// We must have previously published something.
		Assert.assertTrue(null != indexHash);
		
		// Republish the index.
		INetworkScheduler scheduler = environment.getSharedScheduler(local.getSharedConnection(), localIndex.keyName());
		IOperationLog log = environment.logOperation("Republishing index (" + scheduler.getPublicKey() + " -> " + indexHash + ")...");
		IpfsConnectionException error = scheduler.publishIndex(indexHash).get();
		// If we failed to publish, that should be considered an error for this command, since this is all it does.
		if (null != error)
		{
			throw error;
		}
		log.finish("Republish completed!");
	}
}
