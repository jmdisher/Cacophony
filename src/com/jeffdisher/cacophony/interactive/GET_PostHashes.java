package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of post hashes for the given user, as a JSON array.
 */
public class GET_PostHashes implements ValidatedEntryPoints.GET
{
	private final ICommand.Context _context;
	
	public GET_PostHashes(ICommand.Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
		try (IReadingAccess access = StandardAccess.readAccess(_context.environment, _context.logger))
		{
			IpfsKey publicKey = access.getPublicKey();
			IpfsFile lastPublishedIndex = access.getLastRootElement();
			IFolloweeReading followees = access.readableFolloweeData();
			JsonArray hashes = JsonGenerationHelpers.postHashes(access, publicKey, lastPublishedIndex, followees, userToResolve);
			if (null != hashes)
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print(hashes.toString());
			}
			else
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
