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


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
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
		IFolloweeWriting followees = access.writableFolloweeData();
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null != lastRoot)
		{
			throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
		}
		
		ConcurrentFolloweeRefresher refresher = new ConcurrentFolloweeRefresher(environment
				, _publicKey
				, lastRoot
				, access.readPrefs()
				, false
		);
		
		boolean didRefresh = false;
		try {
			// Clean the cache and setup state for the refresh.
			refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION);
			
			// Run the actual refresh.
			didRefresh = refresher.runRefresh();
			
			// Do the cleanup.
			long lastPollMillis = System.currentTimeMillis();
			refresher.finishRefresh(access, followees, lastPollMillis);
		}
		finally
		{
			if (didRefresh)
			{
				log.finish("Follow successful!");
			}
			else
			{
				log.finish("Follow failed!");
			}
		}
	}
}
