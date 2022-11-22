package com.jeffdisher.cacophony.logic;

import java.util.function.Function;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final IEnvironment _environment;
	private final IWritingAccess _access;

	public StandardRefreshSupport(IEnvironment environment, IWritingAccess access)
	{
		_environment = environment;
		_access = access;
	}

	@Override
	public void logMessage(String message)
	{
		_environment.logToConsole(message);
	}
	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _access.getSizeInBytes(cid);
	}
	@Override
	public FuturePin addMetaDataToFollowCache(IpfsFile cid)
	{
		return _access.pin(cid);
	}
	@Override
	public void removeMetaDataFromFollowCache(IpfsFile cid)
	{
		try
		{
			_access.unpin(cid);
		}
		catch (IpfsConnectionException e)
		{
			_environment.logError("Failed to unpin meta-data " + cid + ": " + e.getLocalizedMessage());
		}
	}
	@Override
	public FuturePin addFileToFollowCache(IpfsFile cid)
	{
		return _access.pin(cid);
	}
	@Override
	public void removeFileFromFollowCache(IpfsFile cid)
	{
		try
		{
			_access.unpin(cid);
		}
		catch (IpfsConnectionException e)
		{
			_environment.logError("Failed to unpin file " + cid + ": " + e.getLocalizedMessage());
		}
	}
	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, Function<byte[], R> decoder)
	{
		return _access.loadCached(file, decoder);
	}
}
