package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of post hashes for the given user, as a JSON array.
 */
public class GET_PostHashes implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	
	public GET_PostHashes(IEnvironment environment
			, ILogger logger
	)
	{
		_environment = environment;
		_logger = logger;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
		try (IReadingAccess access = StandardAccess.readAccess(_environment, _logger))
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
		catch (FailedDeserializationException e)
		{
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			e.printStackTrace(response.getWriter());
		}
	}
}
