package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.AddRecommendation;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<None>
{
	@Override
	public None runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IEnvironment.IOperationLog log = environment.logStart("Adding recommendation " + _channelPublicKey + "...");
			 _runCore(environment, access);
			log.logFinish("Now recommending: " + _channelPublicKey);
		}
		return None.NONE;
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IpfsFile newRoot = AddRecommendation.run(access, _channelPublicKey);
		
		if (null != newRoot)
		{
			environment.logVerbose("Publishing " + newRoot + "...");
			FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
			
			// See if the publish actually succeeded (we still want to update our local state, even if it failed).
			CommandHelpers.commonWaitForPublish(environment, asyncPublish);
		}
		else
		{
			throw new UsageException("User was ALREADY recommended");
		}
	}
}
