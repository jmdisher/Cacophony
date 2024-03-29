package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * This command will check if the followee cache is larger than the preferred target size and will shrink it to that
 * target size.  If the cache is already under that threshold, it won't unpin anything.
 * This will also ask IPFS to GC its own storage.
 */
public record CleanCacheCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException
	{
		try (IWritingAccess access = Context.writeAccess(context))
		{
			// First, we want to shrink the local cache.
			CommandHelpers.shrinkCacheToFitInPrefs(context.logger, access, ConcurrentFolloweeRefresher.NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION);
			
			// Even if that didn't do anything, we still want to request that the IPFS node GC.
			access.requestIpfsGc();
		}
		return None.NONE;
	}
}
