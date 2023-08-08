package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.RemoveFavouriteCommand;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Removes the given StreamRecord CID from our list of favourites.  Returns 200 on success and 400 if they were not in
 * the list.
 */
public class DELETE_RemoveFavourite implements ValidatedEntryPoints.DELETE
{
	private final CommandRunner _runner;

	public DELETE_RemoveFavourite(CommandRunner runner
	)
	{
		_runner = runner;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsFile postToRemove = IpfsFile.fromIpfsCid((String)path[2]);
		
		RemoveFavouriteCommand command = new RemoveFavouriteCommand(postToRemove);
		InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
	}
}
