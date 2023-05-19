package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
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
	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddFollowee(CommandRunner runner
			, BackgroundOperations backgroundOperations
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		
		StartFollowingCommand command = new StartFollowingCommand(userToAdd);
		InteractiveHelpers.SuccessfulCommand<None> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, command
		);
		if (null != result)
		{
			result.context().entryRegistry.createNewFollowee(userToAdd);
			_backgroundOperations.enqueueFolloweeRefresh(userToAdd, 0L);
		}
	}
}
