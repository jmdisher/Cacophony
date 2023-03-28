package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			IFolloweeReading followees = access.readableFolloweeData();
			for(IpfsKey followee : followees.getAllKnownFollowees())
			{
				environment.logToConsole("Following: " + followee.toPublicKey());
			}
		}
	}
}
