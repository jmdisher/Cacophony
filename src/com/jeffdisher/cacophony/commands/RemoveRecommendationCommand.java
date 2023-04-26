package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
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
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = context.logger.logStart("Removing recommendation " + _channelPublicKey + "...");
			newRoot = _run(access, _channelPublicKey);
			if (null == newRoot)
			{
				throw new UsageException("User was NOT recommended");
			}
			log.logFinish("No longer recommending: " + _channelPublicKey);
		}
		return new ChangedRoot(newRoot);
	}

	/**
	 * Removes the recommendation from the local user.
	 * 
	 * @param access Write access.
	 * @param userToRemove The user to remove.
	 * @return The new local root element or null, if the user wasn't in the recommended list.
	 * @throws IpfsConnectionException There was a network error.
	 */
	private static IpfsFile _run(IWritingAccess access, IpfsKey userToRemove) throws IpfsConnectionException
	{
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = modifier.loadRecommendations();
		
		// Verify that they are already in the list.
		IpfsFile newRoot = null;
		if (recommendations.getUser().contains(userToRemove.toPublicKey()))
		{
			// Remove the channel.
			recommendations.getUser().remove(userToRemove.toPublicKey());
			
			// Update and commit the structure.
			modifier.storeRecommendations(recommendations);
			newRoot = modifier.commitNewRoot();
		}
		return newRoot;
	}
}
