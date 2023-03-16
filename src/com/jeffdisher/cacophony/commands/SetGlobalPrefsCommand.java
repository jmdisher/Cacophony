package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;


public record SetGlobalPrefsCommand(int _edgeMax, long _followCacheTargetBytes, long _republishIntervalMillis, long _followeeRefreshMillis) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return false;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws UsageException
	{
		PrefsData prefs = access.readPrefs();
		boolean didChange = false;
		
		if (_edgeMax > 0)
		{
			prefs.videoEdgePixelMax = _edgeMax;
			didChange = true;
		}
		if (_followCacheTargetBytes > 0L)
		{
			prefs.followCacheTargetBytes = _followCacheTargetBytes;
			didChange = true;
		}
		if (_republishIntervalMillis > 0L)
		{
			prefs.republishIntervalMillis = _republishIntervalMillis;
			didChange = true;
		}
		if (_followeeRefreshMillis > 0L)
		{
			prefs.followeeRefreshMillis = _followeeRefreshMillis;
			didChange = true;
		}
		if (didChange)
		{
			access.writePrefs(prefs);
			environment.logToConsole("Updated prefs!");
		}
		else
		{
			throw new UsageException("Must specify a postive value for at least one of edge size or cache");
		}
	}
}
