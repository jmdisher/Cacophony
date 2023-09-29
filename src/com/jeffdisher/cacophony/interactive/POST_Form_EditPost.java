package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.EditPostCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Used to update an existing post from the local user's stream.
 * NOTE:  This "edit" support is very minimal, as it doesn't support changes to any of the attachments, only the title,
 * description, and discussionURL.
 * The path variable is the hash of the element to change.  Other data is passed as POST form:
 * -"NAME" - The name (can be empty).
 * -"DESCRIPTION" - The description (can be empty).
 * -"DISCUSSION_URL" - The discussion URL (if this is empty, we will interpret it as "no discussion").
 */
public class POST_Form_EditPost implements ValidatedEntryPoints.POST_Form
{
	public static final String VAR_NAME = "NAME";
	public static final String VAR_DESCRIPTION = "DESCRIPTION";
	public static final String VAR_DISCUSSION_URL = "DISCUSSION_URL";

	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Form_EditPost(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		IpfsKey homePublicKey = (IpfsKey)path[3];
		IpfsFile eltCid = (IpfsFile)path[4];
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String discussionUrl = formVariables.getIfSingle(VAR_DISCUSSION_URL);
		
		EditPostCommand command = new EditPostCommand(eltCid, name, description, discussionUrl);
		InteractiveHelpers.SuccessfulCommand<OnePost> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != success)
		{
			OnePost result = success.result();
			Context context = success.context();
			
			// Now, publish the update.
			_background.requestPublish(context.getSelectedKey(), result.getIndexToPublish());
			
			// Output the new element CID.
			response.setContentType("text/plain");
			response.getWriter().print(result.recordCid.toSafeString());
		}
	}
}
