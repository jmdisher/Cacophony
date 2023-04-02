package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.SimpleFolloweeStarter;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand<None>
{
	@Override
	public None runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException, UsageException, ProtocolDataException, KeyException
	{
		Assert.assertTrue(null != _publicKey);
		
		ILogger log = logger.logStart("Attempting to follow " + _publicKey + "...");
		boolean didRefresh = false;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			IFolloweeWriting followees = access.writableFolloweeData();
			
			// We need to first verify that we aren't already following them.
			IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
			if (null != lastRoot)
			{
				throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
			}
			
			// First, start the follow.
			// Note that this will throw exceptions on failure and never return null.
			IpfsFile hackedRoot = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> log.logOperation(message), access, null, _publicKey);
			
			Assert.assertTrue(null != hackedRoot);
			// If this worked, we will store this temporary root value.  We will do the initial data element refresh only when requested.
			// Save this initial followee state.
			followees.createNewFollowee(_publicKey, hackedRoot);
			didRefresh = true;
		}
		
		if (didRefresh)
		{
			log.logFinish("Follow successful!  Run --refreshFollowee to fetch entries for this user.");
		}
		else
		{
			log.logFinish("Follow failed!");
		}
		return None.NONE;
	}
}
