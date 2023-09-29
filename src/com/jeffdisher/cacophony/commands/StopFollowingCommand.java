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


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand<Incremental>
{
	@Override
	public Incremental runInContext(Context context) throws IpfsConnectionException, UsageException, ProtocolDataException
	{
		if (null == _publicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		ILogger log = context.logger.logStart("Cleaning up to stop following " + _publicKey + "...");
		ConcurrentFolloweeRefresher refresher = null;
		try (IWritingAccess access = Context.writeAccess(context))
		{
			refresher = _setup(context.logger, access);
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
			// There is no real way to fail at this refresh since we are just dropping things.
			Assert.assertTrue(didRefresh);
			// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
			log.logFinish("Cleanup complete.  No longer following " + _publicKey);
		}
		context.cacheUpdater.removedFollowee(_publicKey);
		return new Incremental(moreWork);
	}


	private ConcurrentFolloweeRefresher _setup(ILogger logger, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		FolloweeData followees = access.writableFolloweeData();
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

	private boolean _finish(Context context, IWritingAccess access, ConcurrentFolloweeRefresher refresher) throws IpfsConnectionException, ProtocolDataException
	{
		FolloweeData followees = access.writableFolloweeData();
		long lastPollMillis = context.currentTimeMillisGenerator.getAsLong();
		try
		{
			return refresher.finishRefresh(access, context.cacheUpdater, followees, lastPollMillis);
		}
		catch (KeyException e)
		{
			// The key is not resolved in the "stop following" case.
			throw Assert.unexpected(e);
		}
	}
}
