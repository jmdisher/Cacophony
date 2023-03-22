package com.jeffdisher.cacophony.actions;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The common logic for the different paths to remove a recommendation.
 */
public class RemoveRecommendation
{
	/**
	 * Removes the recommendation from the local user.
	 * 
	 * @param access Write access.
	 * @param userToRemove The user to remove.
	 * @return The new local root element or null, if the user wasn't in the recommended list.
	 * @throws IpfsConnectionException There was a network error.
	 */
	public static IpfsFile run(IWritingAccess access, IpfsKey userToRemove) throws IpfsConnectionException
	{
		ChannelModifier modifier = new ChannelModifier(access);
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = ActionHelpers.readRecommendations(modifier);
		
		// Verify that they are already in the list.
		IpfsFile newRoot = null;
		if (recommendations.getUser().contains(userToRemove.toPublicKey()))
		{
			// Remove the channel.
			recommendations.getUser().remove(userToRemove.toPublicKey());
			
			// Update and commit the structure.
			modifier.storeRecommendations(recommendations);
			newRoot = ActionHelpers.commitNewRoot(modifier);
		}
		return newRoot;
	}

}
