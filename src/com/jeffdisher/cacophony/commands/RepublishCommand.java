package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Since the IPNS publications are temporary (typically only living up to 24 hours), the index hash needs to be
 * periodically republished to the network.
 */
public record RepublishCommand() implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IReadingAccess access) throws IpfsConnectionException, UsageException, KeyException
	{
		// Get the previously posted index hash.
		IpfsFile indexHash = access.getLastRootElement();
		// We must have previously published something.
		Assert.assertTrue(null != indexHash);
		
		// Republish the index.
		IOperationLog log = environment.logOperation("Republishing index (" + access.getPublicKey() + " -> " + indexHash + ")...");
		IpfsConnectionException error = access.republishIndex().get();
		// If we failed to publish, that should be considered an error for this command, since this is all it does.
		if (null != error)
		{
			throw error;
		}
		log.finish("Republish completed!");
	}
}
