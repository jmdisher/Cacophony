package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.commands.ListFolloweesCommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for this user, as a JSON array.
 */
public class GET_FolloweeKeys implements ValidatedEntryPoints.GET
{
	private final CommandRunner _runner;
	
	public GET_FolloweeKeys(CommandRunner runner
	)
	{
		_runner = runner;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		ListFolloweesCommand command = new ListFolloweesCommand();
		InteractiveHelpers.SuccessfulCommand<KeyList> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
		if (null != result)
		{
			JsonArray array = new JsonArray();
			for(IpfsKey followee : result.result().keys)
			{
				array.add(followee.toPublicKey());
			}
			response.setContentType("application/json");
			response.getWriter().print(array.toString());
		}
	}
}
