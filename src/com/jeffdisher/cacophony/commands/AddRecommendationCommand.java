package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand<ChangedRoot>
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
		try (IWritingAccess access = Context.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
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
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		
		// Read the existing recommendations list.
		AbstractRecommendations recommendations = modifier.loadRecommendations();
		
		// Verify that we didn't already add them.
		IpfsFile newRoot = null;
		if (!recommendations.getUserList().contains(userToAdd))
		{
			recommendations.addUser(userToAdd);
			
			// Update and commit the structure.
			modifier.storeRecommendations(recommendations);
			newRoot = modifier.commitNewRoot();
		}
		
		return newRoot;
	}
}
