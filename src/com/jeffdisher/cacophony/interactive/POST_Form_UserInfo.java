package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsFile;
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

	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _background;
	private final LocalUserInfoCache _userInfoCache;

	public POST_Form_UserInfo(IEnvironment environment
			, ILogger logger
			, BackgroundOperations background
			, LocalUserInfoCache userInfoCache
	)
	{
		_environment = environment;
		_logger = logger;
		_background = background;
		_userInfoCache = userInfoCache;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String email = formVariables.getIfSingle(VAR_EMAIL);
		String website = formVariables.getIfSingle(VAR_WEBSITE);
		
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(name, description, null, email, website);
		ChannelDescription result = InteractiveHelpers.runCommandAndHandleErrors(response
				, new ICommand.Context(_environment, _logger, null, null, null)
				, command
		);
		if (null != result)
		{
			// Request the publication.
			_background.requestPublish(result.getIndexToPublish());
			
			// We also want to write this back to the user info cache.
			IpfsKey key = _environment.getPublicKey();
			StreamDescription streamDescription = result.streamDescription;
			_userInfoCache.setUserInfo(key
					, streamDescription.getName()
					, streamDescription.getDescription()
					, IpfsFile.fromIpfsCid(streamDescription.getPicture())
					, streamDescription.getEmail()
					, streamDescription.getWebsite()
			);
		}
	}
}
