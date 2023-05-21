package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for the given user, as a JSON array.
 */
public class GET_RecommendedKeys implements ValidatedEntryPoints.GET
{
	private final CommandRunner _runner;
	
	public GET_RecommendedKeys(CommandRunner runner
	)
	{
		_runner = runner;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		String rawKey = variables[0];
		IpfsKey userToResolve = IpfsKey.fromPublicKey(rawKey);
		if (null != userToResolve)
		{
			// While this could be a home user, we don't bother specializing that case, here.
			ListRecommendationsCommand command = new ListRecommendationsCommand(userToResolve);
			InteractiveHelpers.SuccessfulCommand<KeyList> result = InteractiveHelpers.runCommandAndHandleErrors(response
					, _runner
					, userToResolve
					, command
					, null
			);
			if (null != result)
			{
				JsonArray array = new JsonArray();
				for (IpfsKey key : result.result().keys)
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
