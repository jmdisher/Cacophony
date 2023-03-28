package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshNextFolloweeCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		IOperationLog log = null;
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IFolloweeWriting followees = access.writableFolloweeData();
			
			IpfsKey publicKey = followees.getNextFolloweeToPoll();
			if (null == publicKey)
			{
				throw new UsageException("Not following any users");
			}
			log = environment.logOperation("Refreshing followee " + publicKey + "...");
			refresher = _setup(environment, access, followees, publicKey);
		}
		
		// Run the actual refresh.
		boolean didRefresh = (null != refresher)
				? refresher.runRefresh(null)
				: false
		;
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_finish(environment, access, refresher);
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


	private ConcurrentFolloweeRefresher _setup(IEnvironment environment, IWritingAccess access, IFolloweeWriting followees, IpfsKey publicKey) throws IpfsConnectionException
	{
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(environment
				, publicKey
				, lastRoot
				, access.readPrefs()
				, false
		);
		
		// Clean the cache and setup state for the refresh.
		refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION);
		return refresher;
	}

	private void _finish(IEnvironment environment, IWritingAccess access, ConcurrentFolloweeRefresher refresher)
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		long lastPollMillis = environment.currentTimeMillis();
		refresher.finishRefresh(access, null, null, followees, lastPollMillis);
	}
}
