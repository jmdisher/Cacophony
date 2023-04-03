package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for the given user, as a JSON array.
 */
public class GET_RecommendedKeys implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	
	public GET_RecommendedKeys(IEnvironment environment
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
			ListRecommendationsCommand command = new ListRecommendationsCommand(userToResolve);
			KeyList result = InteractiveHelpers.runCommandAndHandleErrors(response
					, new ICommand.Context(_environment, _logger, null, null, null)
					, command
			);
			if (null != result)
			{
				JsonArray array = new JsonArray();
				for (IpfsKey key : result.keys)
				{
					array.add(key.toPublicKey());
				}
				response.setContentType("application/json");
				response.getWriter().print(array.toString());
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
