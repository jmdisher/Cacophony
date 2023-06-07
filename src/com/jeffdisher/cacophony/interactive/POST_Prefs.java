package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.UsageException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Updates the prefs.
 * This updates all the prefs fields at once, and does so using a form handler (since there is a 1-1 mapping there).
 * In the future, the read/write of the prefs may be converted to the event-based WebSocket.
 */
public class POST_Prefs implements ValidatedEntryPoints.POST_Form
{
	private final Context _context;
	private final BackgroundOperations _operations;
	
	public POST_Prefs(Context context
			, BackgroundOperations operations
	)
	{
		_context = context;
		// We only expose the BackgroundOperations here so that we can notify it when prefs change (we may want to make this a generic listener, but is simple enough).
		_operations = operations;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		int videoEdgePixelMax = _parseInt(formVariables, "videoEdgePixelMax");
		long followCacheTargetBytes = _parseLong(formVariables, "followCacheTargetBytes");
		long republishIntervalMillis = _parseLong(formVariables, "republishIntervalMillis");
		long followeeRefreshMillis = _parseLong(formVariables, "followeeRefreshMillis");
		long explicitCacheTargetBytes = _parseLong(formVariables, "explicitCacheTargetBytes");
		long followeeRecordThumbnailMaxBytes = _parseLong(formVariables, "followeeRecordThumbnailMaxBytes");
		long followeeRecordAudioMaxBytes = _parseLong(formVariables, "followeeRecordAudioMaxBytes");
		long followeeRecordVideoMaxBytes = _parseLong(formVariables, "followeeRecordVideoMaxBytes");
		// Check parameters.
		if ((videoEdgePixelMax < 0)
				|| (followCacheTargetBytes < 1_000_000L)
				|| (republishIntervalMillis < 60_000L)
				|| (followeeRefreshMillis < 60_000L)
				|| (explicitCacheTargetBytes < 1_000_000L)
				|| (followeeRecordThumbnailMaxBytes < 1_000_000L)
				|| (followeeRecordAudioMaxBytes < 1_000_000L)
				|| (followeeRecordVideoMaxBytes < 1_000_000L)
		)
		{
			// We will basically consider this a usage error (caught as general bad request, below).
			throw new UsageException("Invalid parameter");
		}
		boolean didChangeIntervals = false;
		try (IWritingAccess access = StandardAccess.writeAccess(_context))
		{
			PrefsData prefs = access.readPrefs();
			didChangeIntervals = ((prefs.republishIntervalMillis != republishIntervalMillis) || (prefs.followeeRefreshMillis != followeeRefreshMillis));
			prefs.videoEdgePixelMax = videoEdgePixelMax;
			prefs.followCacheTargetBytes = followCacheTargetBytes;
			prefs.republishIntervalMillis = republishIntervalMillis;
			prefs.followeeRefreshMillis = followeeRefreshMillis;
			prefs.explicitCacheTargetBytes = explicitCacheTargetBytes;
			prefs.followeeRecordThumbnailMaxBytes = followeeRecordThumbnailMaxBytes;
			prefs.followeeRecordAudioMaxBytes = followeeRecordAudioMaxBytes;
			prefs.followeeRecordVideoMaxBytes = followeeRecordVideoMaxBytes;
			access.writePrefs(prefs);
		}
		response.setStatus(HttpServletResponse.SC_OK);
		
		if (didChangeIntervals)
		{
			_operations.intervalsWereUpdated(republishIntervalMillis, followeeRefreshMillis);
		}
	}


	private int _parseInt(StringMultiMap<String> formVariables, String key) throws UsageException
	{
		try
		{
			return Integer.parseInt(formVariables.getIfSingle(key));
		}
		catch (NumberFormatException e)
		{
			throw new UsageException("Invalid parameter for: \"" + key + "\"");
		}
	}

	private long _parseLong(StringMultiMap<String> formVariables, String key) throws UsageException
	{
		try
		{
			return Long.parseLong(formVariables.getIfSingle(key));
		}
		catch (NumberFormatException e)
		{
			throw new UsageException("Invalid parameter for: \"" + key + "\"");
		}
	}
}
