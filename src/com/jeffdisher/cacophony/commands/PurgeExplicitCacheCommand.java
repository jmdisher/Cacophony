package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * This command purges the entire contents of the explicit cache, updating pin refcounts and unpinning where required.
 * This will also ask IPFS to GC its own storage.
 */
public record PurgeExplicitCacheCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException
	{
		context.getExplicitCache().purgeCacheFullyAndGc().get();
		return None.NONE;
	}
}
