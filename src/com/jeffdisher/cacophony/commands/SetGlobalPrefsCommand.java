package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;


public record SetGlobalPrefsCommand(int _cacheWidth, int _cacheHeight, long _cacheTotalBytes) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		GlobalPrefs original = local.readPrefs();
		GlobalPrefs prefs = original;
		
		if (_cacheWidth > 0)
		{
			prefs = new GlobalPrefs(_cacheWidth, prefs.cacheHeight(), prefs.cacheTotalBytes());
		}
		if (_cacheHeight > 0)
		{
			prefs = new GlobalPrefs(prefs.cacheWidth(), _cacheHeight, prefs.cacheTotalBytes());
		}
		if (_cacheTotalBytes > 0L)
		{
			prefs = new GlobalPrefs(prefs.cacheWidth(), prefs.cacheHeight(), _cacheTotalBytes);
		}
		if (original != prefs)
		{
			System.out.println("Updated prefs: " + prefs);
		}
		else
		{
			executor.fatalError(new Exception("Must specify a postive value for at least one of width, height, or cache"));
		}
	}
}
