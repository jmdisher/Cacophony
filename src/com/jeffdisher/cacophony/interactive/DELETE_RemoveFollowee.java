package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.StopFollowingCommand;
import com.jeffdisher.cacophony.commands.results.None;
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
	private final Context _context;
	private final BackgroundOperations _backgroundOperations;

	public DELETE_RemoveFollowee(Context context
			, BackgroundOperations backgroundOperations
	)
	{
		_context = context;
		_backgroundOperations = backgroundOperations;
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
						, _context
						, command
				);
				if (null != result)
				{
					_context.entryRegistry.removeFollowee(userToRemove);
					_context.userInfoCache.removeUser(userToRemove);
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
