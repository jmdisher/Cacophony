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
 * Requests that the given public key be added to the list of recommended users.
 * Returns 200 on success, 400 if the given key is invalid or already recommended.
 */
public class POST_Raw_AddRecommendation implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddRecommendation(IEnvironment environment, BackgroundOperations backgroundOperations)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		if (null != userToAdd)
		{
			IpfsFile newRoot = null;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				ChannelModifier modifier = new ChannelModifier(access);
				
				// Read the existing recommendations list.
				StreamRecommendations recommendations = modifier.loadRecommendations();
				
				// Verify that we didn't already add them.
				if (!recommendations.getUser().contains(userToAdd.toPublicKey()))
				{
					// Add the new channel.
					recommendations.getUser().add(userToAdd.toPublicKey());
					
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
