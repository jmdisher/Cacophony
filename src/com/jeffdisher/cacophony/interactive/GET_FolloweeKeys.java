package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ListFolloweesCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for this user, as a JSON array.
 */
public class GET_FolloweeKeys implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	
	public GET_FolloweeKeys(IEnvironment environment
			, ILogger logger
	)
	{
		_environment = environment;
		_logger = logger;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		ListFolloweesCommand command = new ListFolloweesCommand();
		KeyList result = InteractiveHelpers.runCommandAndHandleErrors(response
				, new ICommand.Context(_environment, _logger, null, null, null)
				, command
		);
		if (null != result)
		{
			JsonArray array = new JsonArray();
			for(IpfsKey followee: result.keys)
			{
				array.add(followee.toPublicKey());
			}
			response.setContentType("application/json");
			response.getWriter().print(array.toString());
		}
	}
}
