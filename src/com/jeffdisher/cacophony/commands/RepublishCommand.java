package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Since the IPNS publications are temporary (typically only living up to 24 hours), the index hash needs to be
 * periodically republished to the network.
 */
public record RepublishCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		IOperationLog log = environment.logOperation("Republishing index...");
		LocalConfig local = environment.getLocalConfig();
		// Get the previously posted index hash.
		LocalIndex localIndex = local.readExistingSharedIndex();
		IpfsFile indexHash = localIndex.lastPublishedIndex();
		// We must have previously published something.
		Assert.assertTrue(null != indexHash);
		
		// Republish the index.
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		remote.publishIndex(indexHash);
		local.writeBackConfig();
		log.finish("Republish completed!");
	}
}
