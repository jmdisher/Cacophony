package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _publicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		ILogger log = context.logger.logStart("Refreshing followee " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			refresher = _setup(context.logger, access);
		}
		
		// Run the actual refresh.
		boolean didRefresh = (null != refresher)
				? refresher.runRefresh(null)
				: false
		;
		
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			_finish(context.environment, access, refresher);
		}
		finally
		{
			if (didRefresh)
			{
				log.logFinish("Refresh successful!");
			}
			else
			{
				log.logFinish("Refresh failed!");
			}
		}
		return None.NONE;
	}


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(logger
				, _publicKey
				, lastRoot
				, access.readPrefs()
				, false
		);
		
		// Clean the cache and setup state for the refresh.
		refresher.setupRefresh(access, followees);
		return refresher;
	}

	private void _finish(IEnvironment environment, IWritingAccess access, ConcurrentFolloweeRefresher refresher)
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		long lastPollMillis = environment.currentTimeMillis();
		refresher.finishRefresh(access, null, null, followees, lastPollMillis);
	}
}
