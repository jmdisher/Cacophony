package com.jeffdisher.cacophony.commands;

import java.util.Set;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			Set<IpfsKey> followees = access.readableFolloweeData().getAllKnownFollowees();
			IEnvironment.IOperationLog log = environment.logStart("Found " + followees.size() + " followees:");
			for(IpfsKey followee : followees)
			{
				log.logOperation("Following: " + followee.toPublicKey());
			}
			log.logFinish("");
		}
	}
}
