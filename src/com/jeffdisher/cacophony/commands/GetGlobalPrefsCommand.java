package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.StringHelpers;


public record GetGlobalPrefsCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		GlobalPrefs prefs = local.readSharedPrefs();
		environment.logToConsole("Video preferred bounds: " + prefs.videoEdgePixelMax() + " x " + prefs.videoEdgePixelMax());
		environment.logToConsole("Follower cache target size: " + StringHelpers.humanReadableBytes(prefs.followCacheTargetBytes()));
	}
}
