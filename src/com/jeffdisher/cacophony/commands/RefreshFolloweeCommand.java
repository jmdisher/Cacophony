package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.Incremental;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand<Incremental>
{
	@Override
	public Incremental runInContext(Context context) throws IpfsConnectionException, UsageException, ProtocolDataException, KeyException
	{
		if (null == _publicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		ILogger log = context.logger.logStart("Refreshing followee " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			refresher = _setup(context.logger, access);
		}
		
		// Run the actual refresh.
		boolean didRefresh = refresher.runRefresh(context.cacheUpdater);
		
		boolean moreWork;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			moreWork = _finish(context, access, refresher);
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
		return new Incremental(moreWork);
	}


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		FolloweeData followees = access.writableFolloweeData();
		
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

	private boolean _finish(Context context, IWritingAccess access, ConcurrentFolloweeRefresher refresher) throws IpfsConnectionException, ProtocolDataException, KeyException
	{
		FolloweeData followees = access.writableFolloweeData();
		
		long lastPollMillis = context.currentTimeMillisGenerator.getAsLong();
		return refresher.finishRefresh(access, context.cacheUpdater, followees, lastPollMillis);
	}
}
