package com.jeffdisher.cacophony.interactive;

import java.io.InputStream;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.UpdateDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
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
	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Raw_UserInfo(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		InputStream input = request.getInputStream();
		
		UpdateDescriptionCommand command = new UpdateDescriptionCommand(null, null, input, null, null);
		InteractiveHelpers.SuccessfulCommand<ChannelDescription> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != success)
		{
			ChannelDescription result = success.result();
			Context context = success.context();
			// Request the publication.
			_background.requestPublish(context.getSelectedKey(), result.getIndexToPublish());
			
			// We also want to write this back to the user info cache.
			IpfsKey key = context.getSelectedKey();
			context.userInfoCache.setUserInfo(key
					, result.name
					, result.description
					, result.userPicCid
					, result.email
					, result.website
					, result.feature
			);
			
			// Write out the uploaded file's URL.
			response.getWriter().print(result.userPicUrl);
		}
	}
}
