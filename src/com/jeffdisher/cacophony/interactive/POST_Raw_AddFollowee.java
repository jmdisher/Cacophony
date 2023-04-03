package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.StartFollowingCommand;
import com.jeffdisher.cacophony.commands.results.None;
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
	private final ICommand.Context _context;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddFollowee(ICommand.Context context
			, BackgroundOperations backgroundOperations
	)
	{
		_context = context;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		
		StartFollowingCommand command = new StartFollowingCommand(userToAdd);
		None result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			_context.entryRegistry.createNewFollowee(userToAdd);
			_backgroundOperations.enqueueFolloweeRefresh(userToAdd, 0L);
		}
	}
}
