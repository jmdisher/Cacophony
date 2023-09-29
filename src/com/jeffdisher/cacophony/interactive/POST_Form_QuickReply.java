package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes a reply to another post, based purely on input parameters, without going through normal draft management.
 * Returns 200 on success, 400 if parameters are wrong, or other common InteractiveHelpers error.
 * Variables (at least one of these must be provided but a missing variable means "no change"):
 * -"NAME": The name of the post (cannot be empty).
 * -"DESCRIPTION": The description (cannot be empty).
 * The publishing user and the "replyTo" will be passed as part of the URL.
 */
public class POST_Form_QuickReply implements ValidatedEntryPoints.POST_Form
{
	public static final String VAR_NAME = "NAME";
	public static final String VAR_DESCRIPTION = "DESCRIPTION";

	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;

	public POST_Form_QuickReply(CommandRunner runner
			, BackgroundOperations backgroundOperations
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws IOException
	{
		IpfsKey homePublicKey = (IpfsKey)path[1];
		IpfsFile replyTo = (IpfsFile)path[2];
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		if ((null != name) && !name.isEmpty()
				&& (null != description) && !description.isEmpty()
		)
		{
			PublishCommand command = new PublishCommand(name, description, null, replyTo, null, null, new ElementSubCommand[0]);
			// Now, run the publish.
			InteractiveHelpers.SuccessfulCommand<OnePost> success = InteractiveHelpers.runCommandAndHandleErrors(response
					, _runner
					, homePublicKey
					, command
					, homePublicKey
			);
			if (null != success)
			{
				// Return success, and print the new CID, for tests.
				OnePost result = success.result();
				response.getWriter().write(result.recordCid.toSafeString());
				
				// The publish is something we can wait on, asynchronously, in a different call.
				_backgroundOperations.requestPublish(homePublicKey, result.getIndexToPublish());
			}
			else
			{
				// This status will be set by the common helper.
			}
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().write("Invalid parameters - both NAME and DESCRIPTION must be non-empty");
		}
	}
}
