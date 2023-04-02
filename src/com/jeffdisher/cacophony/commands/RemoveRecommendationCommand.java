package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveRecommendation;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record RemoveRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _channelPublicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = context.logger.logStart("Removing recommendation " + _channelPublicKey + "...");
			newRoot = RemoveRecommendation.run(access, _channelPublicKey);
			if (null == newRoot)
			{
				throw new UsageException("User was NOT recommended");
			}
			log.logFinish("No longer recommending: " + _channelPublicKey);
		}
		return new ChangedRoot(newRoot);
	}
}
