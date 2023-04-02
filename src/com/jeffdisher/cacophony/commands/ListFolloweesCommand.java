package com.jeffdisher.cacophony.commands;

import java.util.Set;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListFolloweesCommand() implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(context.environment, context.logger))
		{
			Set<IpfsKey> followees = access.readableFolloweeData().getAllKnownFollowees();
			ILogger log = context.logger.logStart("Found " + followees.size() + " followees:");
			for(IpfsKey followee : followees)
			{
				log.logOperation("Following: " + followee.toPublicKey());
			}
			log.logFinish("");
		}
		return None.NONE;
	}
}
