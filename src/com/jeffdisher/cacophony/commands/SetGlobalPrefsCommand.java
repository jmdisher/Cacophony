package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record SetGlobalPrefsCommand(int _edgeMax, long _followCacheTargetBytes, long _republishIntervalMillis, long _followeeRefreshMillis) implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}


	private void _runCore(ILogger logger, IWritingAccess access) throws UsageException
	{
		PrefsData prefs = access.readPrefs();
		boolean didChange = false;
		
		if (_edgeMax > 0)
		{
			prefs.videoEdgePixelMax = _edgeMax;
			didChange = true;
		}
		if (_followCacheTargetBytes > 0L)
		{
			prefs.followCacheTargetBytes = _followCacheTargetBytes;
			didChange = true;
		}
		if (_republishIntervalMillis > 0L)
		{
			prefs.republishIntervalMillis = _republishIntervalMillis;
			didChange = true;
		}
		if (_followeeRefreshMillis > 0L)
		{
			prefs.followeeRefreshMillis = _followeeRefreshMillis;
			didChange = true;
		}
		if (didChange)
		{
			access.writePrefs(prefs);
			ILogger log = logger.logStart("Preferences:");
			log.logOperation("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			log.logOperation("Follower cache target size: " + MiscHelpers.humanReadableBytes(prefs.followCacheTargetBytes));
			log.logFinish("Update saved");
		}
		else
		{
			throw new UsageException("Must specify a postive value for at least one of edge size or cache");
		}
	}
}
