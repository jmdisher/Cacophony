package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.ActionHelpers;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<ChangedRoot>
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
			ILogger log = context.logger.logStart("Adding recommendation " + _channelPublicKey + "...");
			newRoot = _run(access, _channelPublicKey);
			if (null == newRoot)
			{
				throw new UsageException("User was ALREADY recommended");
			}
			log.logFinish("Now recommending: " + _channelPublicKey);
		}
		return new ChangedRoot(newRoot);
	}

	/**
	 * Adds the recommendation to the local user.
	 * 
	 * @param access Write access.
	 * @param userToAdd The user to add.
	 * @return The new local root element or null, if the user was already recommended.
	 * @throws IpfsConnectionException There was a network error.
	 */
	private static IpfsFile _run(IWritingAccess access, IpfsKey userToAdd) throws IpfsConnectionException
	{
		ChannelModifier modifier = new ChannelModifier(access);
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = ActionHelpers.readRecommendations(modifier);
		
		// Verify that we didn't already add them.
		IpfsFile newRoot = null;
		if (!recommendations.getUser().contains(userToAdd.toPublicKey()))
		{
			recommendations.getUser().add(userToAdd.toPublicKey());
			
			// Update and commit the structure.
			modifier.storeRecommendations(recommendations);
			newRoot = ActionHelpers.commitNewRoot(modifier);
		}
		
		return newRoot;
	}
}
