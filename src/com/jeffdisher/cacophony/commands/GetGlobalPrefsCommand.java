package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.StringHelpers;


public record GetGlobalPrefsCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			GlobalPrefs prefs = access.readGlobalPrefs();
			environment.logToConsole("Video preferred bounds: " + prefs.videoEdgePixelMax() + " x " + prefs.videoEdgePixelMax());
			environment.logToConsole("Follower cache target size: " + StringHelpers.humanReadableBytes(prefs.followCacheTargetBytes()));
		}
	}
}
