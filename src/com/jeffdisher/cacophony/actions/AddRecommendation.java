package com.jeffdisher.cacophony.actions;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The common logic for the different paths to add a recommendation.
 */
public class AddRecommendation
{
	/**
	 * Adds the recommendation to the local user.
	 * 
	 * @param access Write access.
	 * @param userToAdd The user to add.
	 * @return The new local root element or null, if the user was already recommended.
	 * @throws IpfsConnectionException There was a network error.
	 */
	public static IpfsFile run(IWritingAccess access, IpfsKey userToAdd) throws IpfsConnectionException
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
