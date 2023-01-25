package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given public key be removed from the list of recommended users.
 * Returns 200 on success, 400 if the given key is invalid or not recommended.
 */
public class DELETE_RemoveRecommendation implements ValidatedEntryPoints.DELETE
{
	private final IEnvironment _environment;
	private final BackgroundOperations _backgroundOperations;

	public DELETE_RemoveRecommendation(IEnvironment environment, BackgroundOperations backgroundOperations)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToRemove = IpfsKey.fromPublicKey(variables[0]);
		if (null != userToRemove)
		{
			IpfsFile newRoot = null;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				ChannelModifier modifier = new ChannelModifier(access);
				
				// Read the existing recommendations list.
				StreamRecommendations recommendations = modifier.loadRecommendations();
				
				// Verify that they are already in the list.
				if (recommendations.getUser().contains(userToRemove.toPublicKey()))
				{
					// Remove the channel.
					recommendations.getUser().remove(userToRemove.toPublicKey());
					
					// Update and commit the structure.
					modifier.storeRecommendations(recommendations);
					newRoot = modifier.commitNewRoot();
				}
			}
			if (null != newRoot)
			{
				// Request a republish.
				_backgroundOperations.requestPublish(newRoot);
				response.setStatus(HttpServletResponse.SC_OK);
			}
			else
			{
				// The user was already in the list.
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
		}
		else
		{
			// Invalid key.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}