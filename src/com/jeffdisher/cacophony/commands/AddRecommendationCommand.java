package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.AddRecommendation;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = logger.logStart("Adding recommendation " + _channelPublicKey + "...");
			newRoot = AddRecommendation.run(access, _channelPublicKey);
			if (null == newRoot)
			{
				throw new UsageException("User was ALREADY recommended");
			}
			log.logFinish("Now recommending: " + _channelPublicKey);
		}
		return new ChangedRoot(newRoot);
	}
}
