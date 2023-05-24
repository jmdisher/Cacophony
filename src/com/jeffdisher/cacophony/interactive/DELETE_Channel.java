package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.DeleteChannelCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.interactive.InteractiveHelpers.SuccessfulCommand;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Called to delete a home channel, identified by public key.
 */
public class DELETE_Channel implements ValidatedEntryPoints.DELETE
{
	private final CommandRunner _runner;

	public DELETE_Channel(CommandRunner runner
	)
	{
		_runner = runner;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		
		DeleteChannelCommand command = new DeleteChannelCommand();
		SuccessfulCommand<None> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != result)
		{
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}
}
