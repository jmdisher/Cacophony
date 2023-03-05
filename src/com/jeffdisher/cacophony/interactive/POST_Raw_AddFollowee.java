package com.jeffdisher.cacophony.interactive;

import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.HandoffConnector;
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
	private final Map<IpfsKey, HandoffConnector<IpfsFile, Void>> _connectorsPerUser;
	private final Consumer<Runnable> _connectorDispatcher;

	public POST_Raw_AddFollowee(IEnvironment environment, BackgroundOperations backgroundOperations, Map<IpfsKey, HandoffConnector<IpfsFile, Void>> connectorsPerUser, Consumer<Runnable> connectorDispatcher)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
		_connectorsPerUser = connectorsPerUser;
		_connectorDispatcher = connectorDispatcher;
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
							, false
					);
					
					// Clean the cache and setup state for the refresh.
					refresher.setupRefresh(access, followees, ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION);
				}
			}
			
			if (!isAlreadyFollowed)
			{
				// Create the connector.
				HandoffConnector<IpfsFile, Void> followeeConnector = new HandoffConnector<>(_connectorDispatcher);
				_connectorsPerUser.put(userToAdd, followeeConnector);
				
				// Run the actual refresh.
				boolean didRefresh = (null != refresher)
						? refresher.runRefresh(followeeConnector)
						: false
				;
				
				long lastPollMillis = _environment.currentTimeMillis();
				try (IWritingAccess access = StandardAccess.writeAccess(_environment))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					// TODO: Pass in LocalRecordCache.
					refresher.finishRefresh(access, null, followees, lastPollMillis);
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
