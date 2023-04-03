package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the owning user's preferences as a JSON struct:
 * -edgeSize (int)
 * -followerCacheBytes (long)
 */
public class GET_Prefs implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	
	public GET_Prefs(IEnvironment environment
			, ILogger logger
	)
	{
		_environment = environment;
		_logger = logger;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		try (IReadingAccess access = StandardAccess.readAccess(_environment, _logger))
		{
			PrefsData prefs = access.readPrefs();
			JsonObject userInfo = JsonGenerationHelpers.prefs(prefs);
			// There is no way we can fail to create this object.
			Assert.assertTrue(null != userInfo);
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(userInfo.toString());
		}
	}
}
