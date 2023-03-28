package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
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
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
		boolean didRefresh = false;
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IFolloweeWriting followees = access.writableFolloweeData();
			
			// We need to first verify that we aren't already following them.
			IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
			if (null != lastRoot)
			{
				throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
			}
			
			// First, start the follow.
			IpfsFile hackedRoot = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> environment.logToConsole(message), access, null, _publicKey);
			
			// If this worked, we will store this temporary root value.  We will do the initial data element refresh only when requested.
			if (null != hackedRoot)
			{
				// Save this initial followee state.
				followees.createNewFollowee(_publicKey, hackedRoot, environment.currentTimeMillis());
				didRefresh = true;
			}
		}
		
		if (didRefresh)
		{
			log.finish("Follow successful!  Run --refreshFollowee to fetch entries for this user.");
		}
		else
		{
			log.finish("Follow failed!");
		}
	}
}
