package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.commands.results.None;
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


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException, ProtocolDataException
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
		
		try (IWritingAccess access = Context.writeAccess(context))
		{
			_finish(context, access, refresher);
		}
		catch (IpfsConnectionException e)
		{
			// NOTE:  The finish can technically fail with a connection exception if the daemon is down when we try to
			// stop following.  This would leave the on-node data in an inconsistent state and should be exceptionally
			// rare so we will just log the error and proceed to remove the followee tracking.
			log.logError("WARNING: Network exception occurred during the unfollow so some data may have been leaked on the node: " + e.getLocalizedMessage());
		}
		finally
		{
			// There is no real way to fail at this refresh since we are just dropping things.
			Assert.assertTrue(didRefresh);
			log.logFinish("Cleanup complete.  No longer following " + _publicKey);
		}
		context.cacheUpdater.removedFollowee(_publicKey);
		return None.NONE;
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

	private void _finish(Context context, IWritingAccess access, ConcurrentFolloweeRefresher refresher) throws IpfsConnectionException
	{
		FolloweeData followees = access.writableFolloweeData();
		long lastPollMillis = context.currentTimeMillisGenerator.getAsLong();
		try
		{
			boolean moreToDo = refresher.finishRefresh(access, context.cacheUpdater, followees, lastPollMillis);
			// There is never more incremental work to do for a followee, when unfollowing.
			Assert.assertTrue(!moreToDo);
		}
		catch (KeyException e)
		{
			// The key is not resolved in the "stop following" case.
			throw Assert.unexpected(e);
		}
		catch (ProtocolDataException e)
		{
			// The data we would be reading when stopping the follow is only the data we have already loaded and validated.
			throw Assert.unexpected(e);
		}
	}
}
