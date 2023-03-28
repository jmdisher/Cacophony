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


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Cleaning up to stop following " + _publicKey + "...");
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
			// There is no real way to fail at this refresh since we are just dropping things.
			Assert.assertTrue(didRefresh);
			// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
			log.finish("Cleanup complete.  No longer following " + _publicKey);
		}
	}


	private ConcurrentFolloweeRefresher _setup(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(environment
				, _publicKey
				, lastRoot
				, access.readPrefs()
				, true
		);
		
		// Clean the cache and setup state for the refresh.
		refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION);
		return refresher;
	}

	private void _finish(IEnvironment environment, IWritingAccess access, ConcurrentFolloweeRefresher refresher)
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		long lastPollMillis = environment.currentTimeMillis();
		refresher.finishRefresh(access, null, null, followees, lastPollMillis);
	}
}
