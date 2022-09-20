package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * This command will check if the followee cache is larger than the preferred target size and will shrink it to that
 * target size.  If the cache is already under that threshold, it won't unpin anything.
 * This will also ask IPFS to GC its own storage.
 */
public record CleanCacheCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		// First, we want to shrink the local cache.
		LocalConfig local = environment.loadExistingConfig();
		CommandHelpers.shrinkCacheToFitInPrefs(environment, local);
		
		// Even if that didn't do anything, we still want to request that the IPFS node GC.
		IConnection connection = local.getSharedConnection();
		connection.requestStorageGc();
	}
}
