package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ReadDescriptionCommand;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
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
	private final ICommand.Context _context;
	
	public GET_UnknownUserInfo(ICommand.Context context
	)
	{
		_context = context;
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
					, _context
					, command
			);
			if (null != result)
			{
				JsonObject userInfo = JsonGenerationHelpers.userDescription(result.name
						, result.description
						, result.userPicUrl
						, result.email
						, result.website
				);
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
