package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IOperationLog log = environment.logOperation("Removing recommendation " + _channelPublicKey + "...");
			_runCore(environment, access);
			log.finish("No longer recommending: " + _channelPublicKey);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		ChannelModifier modifier = new ChannelModifier(access);
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = modifier.loadRecommendations();
		
		// Verify that they are already in the list.
		Assert.assertTrue(recommendations.getUser().contains(_channelPublicKey.toPublicKey()));
		
		// Remove the channel.
		recommendations.getUser().remove(_channelPublicKey.toPublicKey());
		
		// Update and commit the structure.
		modifier.storeRecommendations(recommendations);
		environment.logToConsole("Saving new index...");
		IpfsFile newRoot = modifier.commitNewRoot();
		
		environment.logToConsole("Publishing " + newRoot + "...");
		FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
	}
}
