package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the owning user's preferences as a JSON struct:
 * -videoEdgePixelMax (int)
 * -republishIntervalMillis (long)
 * -explicitCacheTargetBytes (long)
 * -explicitUserInfoRefreshMillis (long)
 * -followeeCacheTargetBytes (long)
 * -followeeRefreshMillis (long)
 * -followeeRecordThumbnailMaxBytes (long)
 * -followeeRecordAudioMaxBytes (long)
 * -followeeRecordVideoMaxBytes (long)
 */
public class GET_Prefs implements ValidatedEntryPoints.GET
{
	private final Context _context;
	
	public GET_Prefs(Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		try (IReadingAccess access = StandardAccess.readAccess(_context))
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
