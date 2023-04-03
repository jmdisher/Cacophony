package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsKey;

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
			// First thing, we want to just remove this from background operations.
			boolean didRemove = _backgroundOperations.removeFollowee(userToRemove);
			if (didRemove)
			{
				StopFollowingCommand command = new StopFollowingCommand(userToRemove);
				None result = InteractiveHelpers.runCommandAndHandleErrors(response
						, new ICommand.Context(_environment, _logger, _recordCache, _userInfoCache, _entryRegistry)
						, command
				);
				if (null != result)
				{
					_entryRegistry.removeFollowee(userToRemove);
					_userInfoCache.removeUser(userToRemove);
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
