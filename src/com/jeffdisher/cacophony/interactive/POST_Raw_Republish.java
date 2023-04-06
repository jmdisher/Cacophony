package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.RepublishCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the server republish the home user's root StreamIndex in IPNS.
 */
public class POST_Raw_Republish implements ValidatedEntryPoints.POST_Raw
{
	private final ICommand.Context _context;
	private final BackgroundOperations _background;

	public POST_Raw_Republish(ICommand.Context context
			, BackgroundOperations background
	)
	{
		_context = context;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		RepublishCommand command = new RepublishCommand();
		ChangedRoot result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			_background.requestPublish(result.getIndexToPublish());
		}
	}
}
