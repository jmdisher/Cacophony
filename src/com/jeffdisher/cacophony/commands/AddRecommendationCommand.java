package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.AddRecommendation;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IOperationLog log = environment.logOperation("Adding recommendation " + _channelPublicKey + "...");
			 _runCore(environment, access);
			log.finish("Now recommending: " + _channelPublicKey);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException
	{
		IpfsFile newRoot = AddRecommendation.run(access, _channelPublicKey);
		
		if (null != newRoot)
		{
			environment.logToConsole("Publishing " + newRoot + "...");
			FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
			
			// See if the publish actually succeeded (we still want to update our local state, even if it failed).
			CommandHelpers.commonWaitForPublish(environment, asyncPublish);
		}
		else
		{
			environment.logError("User was already recommended");
		}
	}
}
