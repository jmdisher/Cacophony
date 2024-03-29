package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record GetGlobalPrefsCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException
	{
		try (IReadingAccess access = Context.readAccess(context))
		{
			ILogger log = context.logger.logStart("Preferences:");
			PrefsData prefs = access.readPrefs();
			log.logOperation("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			log.logOperation("Followee cache target size: " + MiscHelpers.humanReadableBytes(prefs.followeeCacheTargetBytes));
			log.logFinish("");
		}
		return None.NONE;
	}
}
