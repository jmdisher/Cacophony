package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _channelPublicKey)
		{
			throw new UsageException("Public key must be provided");
		}
		if (null == context.getSelectedKey())
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			ILogger log = context.logger.logStart("Removing recommendation " + _channelPublicKey + "...");
			newRoot = _run(access, context.enableVersion2Data, _channelPublicKey);
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
	private static IpfsFile _run(IWritingAccess access, boolean enableVersion2Data, IpfsKey userToRemove) throws IpfsConnectionException
	{
		HomeChannelModifier modifier = new HomeChannelModifier(access, enableVersion2Data);
		
		// Read the existing recommendations list.
		AbstractRecommendations recommendations = modifier.loadRecommendations();
		
		// Verify that they are already in the list.
		IpfsFile newRoot = null;
		if (recommendations.getUserList().contains(userToRemove))
		{
			// Remove the channel.
			recommendations.removeUser(userToRemove);
			
			// Update and commit the structure.
			modifier.storeRecommendations(recommendations);
			newRoot = modifier.commitNewRoot();
		}
		return newRoot;
	}
}
