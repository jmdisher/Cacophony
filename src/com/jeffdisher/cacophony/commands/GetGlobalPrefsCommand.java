package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record GetGlobalPrefsCommand() implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(context.environment, context.logger))
		{
			ILogger log = context.logger.logStart("Preferences:");
			PrefsData prefs = access.readPrefs();
			log.logOperation("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			log.logOperation("Follower cache target size: " + MiscHelpers.humanReadableBytes(prefs.followCacheTargetBytes));
			log.logFinish("");
		}
		return None.NONE;
	}
}
