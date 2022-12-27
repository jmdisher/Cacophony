package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshNextFolloweeCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		IpfsKey publicKey = followees.getNextFolloweeToPoll();
		if (null == publicKey)
		{
			throw new UsageException("Not following any users");
		}
		IOperationLog log = environment.logOperation("Refreshing followee " + publicKey + "...");
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(environment
				, publicKey
				, lastRoot
				, access.readPrefs()
				, false
		);
		
		boolean didRefresh = false;
		try {
			// Clean the cache and setup state for the refresh.
			refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION);
			
			// Run the actual refresh.
			didRefresh = refresher.runRefresh();
			
			// Do the cleanup.
			long lastPollMillis = System.currentTimeMillis();
			refresher.finishRefresh(access, followees, lastPollMillis);
		}
		finally
		{
			if (didRefresh)
			{
				log.finish("Refresh successful!");
			}
			else
			{
				log.finish("Refresh failed!");
			}
		}
	}
}
