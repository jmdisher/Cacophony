package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.UsageException;


public record SetGlobalPrefsCommand(int _edgeMax, long _followCacheTargetBytes) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		LocalConfig local = environment.getLocalConfig();
		GlobalPrefs original = local.readSharedPrefs();
		GlobalPrefs prefs = original;
		
		if (_edgeMax > 0)
		{
			prefs = new GlobalPrefs(_edgeMax, prefs.followCacheTargetBytes());
		}
		if (_followCacheTargetBytes > 0L)
		{
			prefs = new GlobalPrefs(prefs.videoEdgePixelMax(), _followCacheTargetBytes);
		}
		if (original != prefs)
		{
			local.storeSharedPrefs(prefs);
			environment.logToConsole("Updated prefs: " + prefs);
		}
		else
		{
			throw new UsageException("Must specify a postive value for at least one of edge size or cache");
		}
	}
}
