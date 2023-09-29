package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.commands.results.Incremental;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshNextFolloweeCommand() implements ICommand<Incremental>
{
	@Override
	public Incremental runInContext(Context context) throws IpfsConnectionException, UsageException, ProtocolDataException, KeyException
	{
		ILogger log;
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = Context.writeAccess(context))
		{
			FolloweeData followees = access.writableFolloweeData();
			
			IpfsKey publicKey = followees.getNextFolloweeToPoll();
			if (null == publicKey)
			{
				throw new UsageException("Not following any users");
			}
			log = context.logger.logStart("Refreshing followee " + publicKey + "...");
			refresher = _setup(context.logger, access, followees, publicKey);
		}
		
		// Run the actual refresh.
		boolean didRefresh = refresher.runRefresh(context.cacheUpdater);
		
		boolean moreWork;
		try (IWritingAccess access = Context.writeAccess(context))
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


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access, FolloweeData followees, IpfsKey publicKey) throws IpfsConnectionException
	{
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(logger
				, publicKey
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
