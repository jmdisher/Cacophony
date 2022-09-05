package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.StringHelpers;


public record GetGlobalPrefsCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		GlobalPrefs prefs = null;
		try (IReadOnlyLocalData localData = local.getSharedLocalData().openForRead())
		{
			prefs = localData.readGlobalPrefs();
		}
		environment.logToConsole("Video preferred bounds: " + prefs.videoEdgePixelMax() + " x " + prefs.videoEdgePixelMax());
		environment.logToConsole("Follower cache target size: " + StringHelpers.humanReadableBytes(prefs.followCacheTargetBytes()));
	}
}
