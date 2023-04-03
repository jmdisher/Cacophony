package com.jeffdisher.cacophony.interactive;

import java.io.InputStream;

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
 * Used for updating the user info image for the home user.
 * Ideally, this would be combined with POST_Form_UserInfo but the current BreakWater multi-part implementation has size
 * limits so the image needs to be updated through this different path.
 */
public class POST_Raw_UserInfo implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _background;
	private final LocalUserInfoCache _userInfoCache;

	public POST_Raw_UserInfo(IEnvironment environment
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
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		InputStream input = request.getInputStream();
		
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(null, null, input, null, null);
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
			
			// Write out the uploaded file's URL.
			response.getWriter().print(result.userPicUrl);
		}
	}
}
