package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given followee key be added to the list of followed users.
 * Returns synchronously, but may be slow as it needs to find the followee.  Returns 200 on success, 404 if the followee
 * is not found, 400 if the given key is invalid.
 */
public class POST_Raw_AddFollowee implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddFollowee(IEnvironment environment, BackgroundOperations backgroundOperations)
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
			ConcurrentFolloweeRefresher refresher = null;
			boolean isAlreadyFollowed = false;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				IFolloweeWriting followees = access.writableFolloweeData();
				IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(userToAdd);
				isAlreadyFollowed = (null != lastRoot);
				if (!isAlreadyFollowed)
				{
					refresher = new ConcurrentFolloweeRefresher(_environment
							, userToAdd
							, lastRoot
							, access.readPrefs()
							, null
							, false
					);
					
					// Clean the cache and setup state for the refresh.
					refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION);
				}
			}
			
			if (!isAlreadyFollowed)
			{
				// Run the actual refresh.
				boolean didRefresh = (null != refresher)
						? refresher.runRefresh()
						: false
				;
				
				long lastPollMillis = System.currentTimeMillis();
				try (IWritingAccess access = StandardAccess.writeAccess(_environment))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					refresher.finishRefresh(access, followees, lastPollMillis);
				}
				finally
				{
					if (didRefresh)
					{
						// Add this to the background operations so it will be refreshed again.
						_backgroundOperations.enqueueFolloweeRefresh(userToAdd, lastPollMillis);
						response.setStatus(HttpServletResponse.SC_OK);
					}
					else
					{
						// We don't know who this is.
						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
					}
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