package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.MiscHelpers;


public record GetGlobalPrefsCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			PrefsData prefs = access.readPrefs();
			environment.logToConsole("Video preferred bounds: " + prefs.videoEdgePixelMax + " x " + prefs.videoEdgePixelMax);
			environment.logToConsole("Follower cache target size: " + MiscHelpers.humanReadableBytes(prefs.followCacheTargetBytes));
		}
	}
}
