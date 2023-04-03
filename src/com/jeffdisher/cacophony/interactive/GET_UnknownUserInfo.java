package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
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
 */
public class GET_UnknownUserInfo implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	
	public GET_UnknownUserInfo(IEnvironment environment
			, ILogger logger
	)
	{
		_environment = environment;
		_logger = logger;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		String rawKey = variables[0];
		IpfsKey userToResolve = IpfsKey.fromPublicKey(rawKey);
		if (null != userToResolve)
		{
			ReadDescriptionCommand command = new ReadDescriptionCommand(userToResolve);
			ChannelDescription result = InteractiveHelpers.runCommandAndHandleErrors(response
					, new ICommand.Context(_environment, _logger, null, null, null)
					, command
			);
			if (null != result)
			{
				JsonObject userInfo = JsonGenerationHelpers.populateJsonForUnknownDescription(result.streamDescription, result.userPicUrl);
				response.setContentType("application/json");
				response.getWriter().print(userInfo.toString());
			}
		}
		else
		{
			// This happens if the key is invalid.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().print("Invalid key: " + rawKey);
		}
	}
}
