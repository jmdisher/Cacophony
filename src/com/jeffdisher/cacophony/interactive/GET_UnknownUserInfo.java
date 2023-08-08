package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Looks up description information for an unknown user, by key.
 * Returns build information as a JSON struct:
 * -name
 * -description
 * -userPicUrl
 * -email
 * -website
 * -feature
 */
public class GET_UnknownUserInfo implements ValidatedEntryPoints.GET
{
	private final CommandRunner _runner;
	
	public GET_UnknownUserInfo(CommandRunner runner
	)
	{
		_runner = runner;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey userToResolve = (IpfsKey)path[2];
		// While this could be a home user, we don't bother specializing that case, here.
		ReadDescriptionCommand command = new ReadDescriptionCommand(userToResolve);
		InteractiveHelpers.SuccessfulCommand<ChannelDescription> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, userToResolve
				, command
				, null
		);
		if (null != success)
		{
			ChannelDescription result = success.result();
			JsonObject userInfo = JsonGenerationHelpers.userDescription(result.name
					, result.description
					, result.userPicUrl
					, result.email
					, result.website
					, result.feature
			);
			response.setContentType("application/json");
			response.getWriter().print(userInfo.toString());
		}
	}
}
