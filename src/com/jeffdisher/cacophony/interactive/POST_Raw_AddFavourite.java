package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.AddFavouriteCommand;
import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Adds the given StreamRecord CID to our list of favourites.  Returns 200 on success and 400 if they were already in
 * the list.
 */
public class POST_Raw_AddFavourite implements ValidatedEntryPoints.POST_Raw
{
	private final CommandRunner _runner;

	public POST_Raw_AddFavourite(CommandRunner runner
	)
	{
		_runner = runner;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsFile postToAdd = (IpfsFile)path[2];
		
		AddFavouriteCommand command = new AddFavouriteCommand(postToAdd);
		InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
	}
}
