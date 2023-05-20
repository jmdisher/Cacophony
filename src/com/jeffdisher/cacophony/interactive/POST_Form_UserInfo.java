package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Used for updating the user info for the home user.
 * Ideally, this would be combined with POST_Raw_UserInfo but the current BreakWater multi-part implementation has size
 * limits so the image needs to be updated through that other path while the rest of the form data is sent through here.
 * Note that we assume all form variables are sent, although email and website can be empty (will be stored as null, in
 * that case).
 */
public class POST_Form_UserInfo implements ValidatedEntryPoints.POST_Form
{
	public static final String VAR_NAME = "NAME";
	public static final String VAR_DESCRIPTION = "DESCRIPTION";
	public static final String VAR_EMAIL = "EMAIL";
	public static final String VAR_WEBSITE = "WEBSITE";

	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Form_UserInfo(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String email = formVariables.getIfSingle(VAR_EMAIL);
		String website = formVariables.getIfSingle(VAR_WEBSITE);
		
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(name, description, null, email, website);
		InteractiveHelpers.SuccessfulCommand<ChannelDescription> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
		);
		if (null != success)
		{
			ChannelDescription result = success.result();
			Context context = success.context();
			// Request the publication.
			_background.requestPublish(context.keyName, result.getIndexToPublish());
			
			// We also want to write this back to the user info cache.
			IpfsKey key = context.getSelectedKey();
			context.userInfoCache.setUserInfo(key
					, result.name
					, result.description
					, result.userPicCid
					, result.email
					, result.website
			);
		}
	}
}
