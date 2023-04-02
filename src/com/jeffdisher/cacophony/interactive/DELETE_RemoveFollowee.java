package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.ConcurrentFolloweeRefresher;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given followee key be removed from the list of followed users.
 * Returns synchronously, but may be not be fast as it needs to do some cleanup.  Returns 200 on success, 404 if the
 * followee is not one we are following, 400 if the given key is invalid.
 */
public class DELETE_RemoveFollowee implements ValidatedEntryPoints.DELETE
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _backgroundOperations;
	private final LocalRecordCache _recordCache;
	private final LocalUserInfoCache _userInfoCache;
	private final EntryCacheRegistry _entryRegistry;

	public DELETE_RemoveFollowee(IEnvironment environment
			, ILogger logger
			, BackgroundOperations backgroundOperations
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
	)
	{
		_environment = environment;
		_logger = logger;
		_backgroundOperations = backgroundOperations;
		_recordCache = recordCache;
		_userInfoCache = userInfoCache;
		_entryRegistry = entryRegistry;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToRemove = IpfsKey.fromPublicKey(variables[0]);
		if (null != userToRemove)
		{
			ConcurrentFolloweeRefresher refresher = null;
			boolean isAlreadyFollowed = false;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment, _logger))
			{
				IFolloweeWriting followees = access.writableFolloweeData();
				IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(userToRemove);
				isAlreadyFollowed = (null != lastRoot);
				if (isAlreadyFollowed)
				{
					refresher = new ConcurrentFolloweeRefresher(_logger
							, userToRemove
							, lastRoot
							, access.readPrefs()
							, true
					);
					
					// Clean the cache and setup state for the refresh.
					refresher.setupRefresh(access, followees);
				}
			}
			
			if (isAlreadyFollowed)
			{
				// Run the actual refresh.
				boolean didRefresh = (null != refresher)
						? refresher.runRefresh(_entryRegistry)
						: false
				;
				
				try (IWritingAccess access = StandardAccess.writeAccess(_environment, _logger))
				{
					IFolloweeWriting followees = access.writableFolloweeData();
					long lastPollMillis = _environment.currentTimeMillis();
					refresher.finishRefresh(access, _recordCache, _userInfoCache, followees, lastPollMillis);
				}
				finally
				{
					// There is no real way to fail at this refresh since we are just dropping things.
					Assert.assertTrue(didRefresh);
					boolean didRemove = _backgroundOperations.removeFollowee(userToRemove);
					// This removal could only fail as a result of racy calls to this end-point.
					if (didRemove)
					{
						_entryRegistry.removeFollowee(userToRemove);
						_userInfoCache.removeUser(userToRemove);
					}
					else
					{
						_logger.logError("Followee failed to be removed: " + userToRemove);
					}
					response.setStatus(HttpServletResponse.SC_OK);
				}
			}
			else
			{
				// We don't follow them so this is not found.
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		else
		{
			// Invalid key.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
