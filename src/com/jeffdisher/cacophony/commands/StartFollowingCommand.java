package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException, ProtocolDataException, KeyException
	{
		if (null == _publicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		ILogger log = context.logger.logStart("Attempting to follow " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			refresher = _setup(log, access);
		}
		
		// Run the actual refresh.
		boolean didRefresh = (null != refresher)
				? refresher.runRefresh(context.cacheUpdater)
				: false
		;

		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			_finish(context, access, refresher);
		}
		finally
		{
			if (didRefresh)
			{
				log.logFinish("Follow successful!");
			}
			else
			{
				log.logFinish("Follow failed!");
			}
		}
		return None.NONE;
	}


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		FolloweeData followees = access.writableFolloweeData();
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null != lastRoot)
		{
			throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
		}
		// Make sure that this isn't a local user.
		if (access.readHomeUserData().stream()
				.anyMatch((IReadingAccess.HomeUserTuple tuple) -> tuple.publicKey().equals(_publicKey)))
		{
			throw new UsageException("Cannot follow on of the home users");
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

	private void _finish(Context context, IWritingAccess access, ConcurrentFolloweeRefresher refresher) throws IpfsConnectionException, ProtocolDataException, KeyException
	{
		FolloweeData followees = access.writableFolloweeData();
		long lastPollMillis = context.currentTimeMillisGenerator.getAsLong();
		refresher.finishRefresh(access, context.cacheUpdater, followees, lastPollMillis);
	}
}
