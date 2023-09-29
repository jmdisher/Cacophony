package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.PurgeExplicitCacheCommand;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the explicit cache is purged and then a GC is run on the IPFS node.
 * Returns nothing.
 */
public class POST_Raw_ClearExplicitCache implements ValidatedEntryPoints.POST_Raw
{
	private final CommandRunner _runner;

	public POST_Raw_ClearExplicitCache(CommandRunner runner
	)
	{
		_runner = runner;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		PurgeExplicitCacheCommand command = new PurgeExplicitCacheCommand();
		InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
	}
}
