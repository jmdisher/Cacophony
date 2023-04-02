package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record GetGlobalPrefsCommand() implements ICommand<None>
{
	@Override
	public None runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment, logger))
		{
			ILogger log = logger.logStart("Preferences:");
			PrefsData prefs = access.readPrefs();
			log.logOperation("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			log.logOperation("Follower cache target size: " + MiscHelpers.humanReadableBytes(prefs.followCacheTargetBytes));
			log.logFinish("");
		}
		return None.NONE;
	}
}
