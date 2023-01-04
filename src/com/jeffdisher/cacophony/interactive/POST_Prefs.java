package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.UsageException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Updates the prefs.
 * This updates all the prefs fields at once, and does so using a form handler (since there is a 1-1 mapping there).
 * In the future, the read/write of the prefs may be converted to the event-based WebSocket.
 */
public class POST_Prefs implements IPostFormHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	private final BackgroundOperations _operations;
	
	public POST_Prefs(IEnvironment environment, String xsrf, BackgroundOperations operations)
	{
		_environment = environment;
		_xsrf = xsrf;
		// We only expose the BackgroundOperations here so that we can notify it when prefs change (we may want to make this a generic listener, but is simple enough).
		_operations = operations;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			try
			{
				int edgeSize = Integer.parseInt(formVariables.getIfSingle("edgeSize"));
				long followerCacheBytes = Long.parseLong(formVariables.getIfSingle("followerCacheBytes"));
				long republishIntervalMillis = Long.parseLong(formVariables.getIfSingle("republishIntervalMillis"));
				long followeeRefreshMillis = Long.parseLong(formVariables.getIfSingle("followeeRefreshMillis"));
				// Check parameters.
				if ((edgeSize < 0)
						|| (followerCacheBytes < 1_000_000_000L)
						|| (republishIntervalMillis < 60_000L)
						|| (followeeRefreshMillis < 60_000L)
				)
				{
					// We will basically consider this a usage error (caught as general bad request, below).
					throw new UsageException("Invalid parameter");
				}
				boolean didChangeIntervals = false;
				try (IWritingAccess access = StandardAccess.writeAccess(_environment))
				{
					PrefsData prefs = access.readPrefs();
					didChangeIntervals = ((prefs.republishIntervalMillis != republishIntervalMillis) || (prefs.followeeRefreshMillis != followeeRefreshMillis));
					prefs.videoEdgePixelMax = edgeSize;
					prefs.followCacheTargetBytes = followerCacheBytes;
					prefs.republishIntervalMillis = republishIntervalMillis;
					prefs.followeeRefreshMillis = followeeRefreshMillis;
					access.writePrefs(prefs);
				}
				response.setStatus(HttpServletResponse.SC_OK);
				
				if (didChangeIntervals)
				{
					_operations.intervalsWereUpdated(republishIntervalMillis, followeeRefreshMillis);
				}
			}
			catch (Throwable t)
			{
				// If anything odd happens here (usually a number format issue), just say that the call was invalid.
				_environment.logError("POST /prefs ERROR: " + t.getLocalizedMessage());
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
		}
	}
}
