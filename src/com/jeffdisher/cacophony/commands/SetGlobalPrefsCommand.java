package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;


public record SetGlobalPrefsCommand(int _edgeMax, long _followCacheTargetBytes) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		GlobalPrefs original = local.readPrefs();
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
			System.out.println("Updated prefs: " + prefs);
		}
		else
		{
			executor.fatalError(new Exception("Must specify a postive value for at least one of edge size or cache"));
		}
	}
}
