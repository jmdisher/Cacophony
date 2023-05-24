package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.CreateChannelCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that a new channel be created with the given key name, and the current channel is changed to that user.
 * Returns 200 on success (or if they already were selected), 400 if the key name is unknown.
 */
public class POST_Raw_NewChannel implements ValidatedEntryPoints.POST_Raw
{
	private final Context _context;
	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Raw_NewChannel(Context context
			, CommandRunner runner
			, BackgroundOperations background
	)
	{
		_context = context;
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		String keyName = pathVariables[0];
		
		// We can't use the runner for this case since the key doesn't exist and it requires that so we invoke, directly.
		CreateChannelCommand command = new CreateChannelCommand(keyName);
		InteractiveHelpers.SuccessfulCommand<ChangedRoot> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
		if (null != result)
		{
			// We want to set the selected name to be this new one.
			Context subContext = result.context();
			IpfsKey selectedKey = subContext.getSelectedKey();
			_context.setSelectedKey(selectedKey);
			_background.addChannel(keyName, selectedKey, result.result().getIndexToPublish());
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}
}
