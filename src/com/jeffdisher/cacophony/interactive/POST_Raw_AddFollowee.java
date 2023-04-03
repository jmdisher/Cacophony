package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsKey;

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
	private final ILogger _logger;
	private final BackgroundOperations _backgroundOperations;
	private final LocalUserInfoCache _userInfoCache;
	private final EntryCacheRegistry _entryRegistry;

	public POST_Raw_AddFollowee(IEnvironment environment
			, ILogger logger
			, BackgroundOperations backgroundOperations
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
	)
	{
		_environment = environment;
		_logger = logger;
		_backgroundOperations = backgroundOperations;
		_userInfoCache = userInfoCache;
		_entryRegistry = entryRegistry;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		
		StartFollowingCommand command = new StartFollowingCommand(userToAdd);
		None result = InteractiveHelpers.runCommandAndHandleErrors(response
				, new ICommand.Context(_environment, _logger, null, _userInfoCache, null)
				, command
		);
		if (null != result)
		{
			_entryRegistry.createNewFollowee(userToAdd);
			_backgroundOperations.enqueueFolloweeRefresh(userToAdd, 0L);
		}
	}
}
