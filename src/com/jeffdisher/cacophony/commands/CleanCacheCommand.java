package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			// First, we want to shrink the local cache.
			CommandHelpers.shrinkCacheToFitInPrefs(environment, access, 1.0);
			
			// Even if that didn't do anything, we still want to request that the IPFS node GC.
			access.requestIpfsGc();
		}
	}
}
