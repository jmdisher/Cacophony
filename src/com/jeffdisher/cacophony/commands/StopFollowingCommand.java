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


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IOperationLog log = environment.logOperation("Cleaning up to stop following " + _publicKey + "...");
		
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
		
		boolean didRefresh = false;
		try {
			// Clean the cache and setup state for the refresh.
			refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION);
			
			// Run the actual refresh.
			didRefresh = refresher.runRefresh();
			
			// Do the cleanup.
			long lastPollMillis = System.currentTimeMillis();
			refresher.finishRefresh(access, followees, lastPollMillis);
		}
		finally
		{
			// There is no real way to fail at this refresh since we are just dropping things.
			Assert.assertTrue(didRefresh);
			// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
			log.finish("Cleanup complete.  No longer following " + _publicKey);
		}
	}
}
