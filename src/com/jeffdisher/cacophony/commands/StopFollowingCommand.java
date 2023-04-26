package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _publicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		ILogger log = context.logger.logStart("Cleaning up to stop following " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			refresher = _setup(context.logger, access);
		}
		
		// Run the actual refresh.
		boolean didRefresh = (null != refresher)
				? refresher.runRefresh(context.entryRegistry)
				: false
		;
		
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			_finish(context, access, refresher);
		}
		finally
		{
			// There is no real way to fail at this refresh since we are just dropping things.
			Assert.assertTrue(didRefresh);
			// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
			log.logFinish("Cleanup complete.  No longer following " + _publicKey);
		}
		return None.NONE;
	}


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(logger
				, _publicKey
				, lastRoot
				, access.readPrefs()
				, true
		);
		
		// Clean the cache and setup state for the refresh.
		refresher.setupRefresh(access, followees);
		return refresher;
	}

	private void _finish(ICommand.Context context, IWritingAccess access, ConcurrentFolloweeRefresher refresher)
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		long lastPollMillis = context.environment.currentTimeMillis();
		refresher.finishRefresh(access, context.recordCache, context.userInfoCache, followees, lastPollMillis);
	}
}
