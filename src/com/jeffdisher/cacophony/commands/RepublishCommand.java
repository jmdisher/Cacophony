package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Since the IPNS publications are temporary (typically only living up to 24 hours), the index hash needs to be
 * periodically republished to the network.
 */
public record RepublishCommand() implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		// Get the previously posted index hash.
		LocalIndex index = local.readIndex();
		// The channel needs to exist.
		Assert.assertTrue(null != index);
		IpfsFile indexHash = index.lastPublishedIndex();
		// We must have previously published something.
		Assert.assertTrue(null != indexHash);
		
		// Republish the index.
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local);
		remote.publishIndex(indexHash);
	}
}
