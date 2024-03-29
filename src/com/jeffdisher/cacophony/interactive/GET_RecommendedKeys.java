package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.ListRecommendationsCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
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
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey userToResolve = (IpfsKey)path[2];
		// While this could be a home user, we don't bother specializing that case, here.
		ListRecommendationsCommand command = new ListRecommendationsCommand(userToResolve);
		// This is just a reading case so we don't bother with blocking key.
		InteractiveHelpers.SuccessfulCommand<KeyList> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
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
}
