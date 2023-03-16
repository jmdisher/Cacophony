package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.SimpleFolloweeStarter;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return false;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
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
		
		if (didRefresh)
		{
			try (IWritingAccess access = StandardAccess.writeAccess(environment))
			{
				_finish(environment, access, refresher);
			}
			finally
			{
				log.finish("Follow successful!");
			}
		}
		else
		{
			log.finish("Follow failed!");
		}
	}


	private ConcurrentFolloweeRefresher _setup(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null != lastRoot)
		{
			throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
		}
		
		// First, start the follow.
		IpfsFile hackedRoot = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> environment.logToConsole(message), access, _publicKey);
		
		// Proceed to a normal refresh if this worked.
		ConcurrentFolloweeRefresher refresher = null;
		if (null != hackedRoot)
		{
			// Save this initial followee state.
			followees.createNewFollowee(_publicKey, hackedRoot, environment.currentTimeMillis());
			
			// Now, proceed as a normal refresh of an existing followee.
			refresher = new ConcurrentFolloweeRefresher(environment
					, _publicKey
					, hackedRoot
					, access.readPrefs()
					, false
			);
			
			// Clean the cache and setup state for the refresh.
			refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION);
		}
		
		return refresher;
	}

	private void _finish(IEnvironment environment, IWritingAccess access, ConcurrentFolloweeRefresher refresher)
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		long lastPollMillis = environment.currentTimeMillis();
		refresher.finishRefresh(access, null, followees, lastPollMillis);
	}
}
