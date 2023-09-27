package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.DeleteChannelCommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Called to delete a home channel, identified by public key.
 */
public class DELETE_Channel implements ValidatedEntryPoints.DELETE
{
	private final Context _context;
	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public DELETE_Channel(Context context
			, CommandRunner runner
			, BackgroundOperations background
	)
	{
		_context = context;
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey homePublicKey = (IpfsKey)path[3];
		
		DeleteChannelCommand command = new DeleteChannelCommand();
		InteractiveHelpers.SuccessfulCommand<None> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != result)
		{
			// Account for however the sub-command managed the context.
			Context subContext = result.context();
			IpfsKey selectedKey = subContext.getSelectedKey();
			_context.setSelectedKey(selectedKey);
			// Remove this from the background publisher.
			_background.removeChannel(homePublicKey);
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}
}
