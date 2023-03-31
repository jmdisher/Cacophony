package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.logic.SimpleFolloweeStarter;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given followee key be added to the list of followed users.
 * The actual refresh runs asynchronously, but looking up and adding the base meta-data for the followee is done
 * synchronously.  Returns 200 on success, 404 if the followee is not found, 400 if the given key is invalid.
 */
public class POST_Raw_AddFollowee implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final BackgroundOperations _backgroundOperations;
	private final LocalUserInfoCache _userInfoCache;
	private final EntryCacheRegistry _entryRegistry;

	public POST_Raw_AddFollowee(IEnvironment environment
			, BackgroundOperations backgroundOperations
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
	)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
		_userInfoCache = userInfoCache;
		_entryRegistry = entryRegistry;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		if (null != userToAdd)
		{
			boolean isAlreadyFollowed = false;
			boolean didAddFollowee = false;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				IFolloweeWriting followees = access.writableFolloweeData();
				IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(userToAdd);
				isAlreadyFollowed = (null != lastRoot);
				if (!isAlreadyFollowed)
				{
					// First, start the follow.
					IpfsFile hackedRoot;
					try
					{
						hackedRoot = SimpleFolloweeStarter.startFollowingWithEmptyRecords((String message) -> _environment.logVerbose(message), access, _userInfoCache, userToAdd);
						Assert.assertTrue(null != hackedRoot);
					}
					catch (IpfsConnectionException | ProtocolDataException | KeyException e)
					{
						// For now, we won't do anything special with these - we just interpret them as failures to add.
						hackedRoot = null;
					}
					
					// If that worked, save back the followee and request a refresh.
					if (null != hackedRoot)
					{
						// Create the new followee record, saying we never refreshed it (since this is only a hacked element).
						followees.createNewFollowee(userToAdd, hackedRoot);
						
						// Create the connector.
						_entryRegistry.createNewFollowee(userToAdd);
						
						// Add this to the background operations to be refreshed next.
						_backgroundOperations.enqueueFolloweeRefresh(userToAdd, 0L);
						
						didAddFollowee = true;
					}
				}
			}
			
			if (!isAlreadyFollowed)
			{
				if (didAddFollowee)
				{
					// Complete success - although the follow refresh will happen in the background.
					response.setStatus(HttpServletResponse.SC_OK);
				}
				else
				{
					// We don't know who this is.
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
			else
			{
				// Already followed.
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
