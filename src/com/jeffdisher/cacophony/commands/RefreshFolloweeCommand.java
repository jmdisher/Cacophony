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


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Refreshing followee " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			refresher = _setup(environment, access);
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


	private ConcurrentFolloweeRefresher _setup(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(environment
				, _publicKey
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
		refresher.finishRefresh(access, followees, lastPollMillis);
	}
}
