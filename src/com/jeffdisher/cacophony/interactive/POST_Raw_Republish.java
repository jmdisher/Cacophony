package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.RepublishCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the server republish the home user's root StreamIndex in IPNS.
 */
public class POST_Raw_Republish implements ValidatedEntryPoints.POST_Raw
{
	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Raw_Republish(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		IpfsKey homePublicKey = (IpfsKey)path[2];
		// Note that we don't republish with the blocking key since it is designed to not interact with on-IPFS user state.
		RepublishCommand command = new RepublishCommand();
		InteractiveHelpers.SuccessfulCommand<ChangedRoot> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != result)
		{
			_background.requestPublish(result.context().getSelectedKey(), result.result().getIndexToPublish());
		}
	}
}
