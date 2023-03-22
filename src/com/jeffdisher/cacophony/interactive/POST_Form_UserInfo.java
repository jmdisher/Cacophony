package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.UpdateDescription;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
	private final BackgroundOperations _background;
	private final LocalUserInfoCache _userInfoCache;

	public POST_Form_UserInfo(IEnvironment environment, BackgroundOperations background, LocalUserInfoCache userInfoCache)
	{
		_environment = environment;
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
		
		// All of these must be non-null.
		if ((null != name)
				&& (null != description)
				&& (null != email)
				&& (null != website)
				// These elements must also be non-empty.
				&& !name.isEmpty()
				&& !description.isEmpty()
		)
		{
			StreamDescription streamDescription;
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				UpdateDescription.Result result = UpdateDescription.run(access, name, description, null, email, website);
				IpfsFile newRoot = result.newRoot();
				streamDescription = result.updatedStreamDescription();
				
				_background.requestPublish(newRoot);
				response.setStatus(HttpServletResponse.SC_OK);
			}
			
			// We also want to write this back to the user info cache.
			IpfsKey key = _environment.getPublicKey();
			_userInfoCache.setUserInfo(key
					, streamDescription.getName()
					, streamDescription.getDescription()
					, IpfsFile.fromIpfsCid(streamDescription.getPicture())
					, streamDescription.getEmail()
					, streamDescription.getWebsite()
			);
		}
		else
		{
			// Missing variables.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
