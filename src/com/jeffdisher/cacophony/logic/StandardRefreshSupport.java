package com.jeffdisher.cacophony.logic;

import java.util.function.Function;

import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final IEnvironment _environment;
	private final INetworkScheduler _scheduler;
	private final HighLevelCache _cache;

	public StandardRefreshSupport(IEnvironment environment, INetworkScheduler scheduler, HighLevelCache cache)
	{
		_environment = environment;
		_scheduler = scheduler;
		_cache = cache;
	}

	@Override
	public void logMessage(String message)
	{
		_environment.logToConsole(message);
	}
	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _scheduler.getSizeInBytes(cid);
	}
	@Override
	public FuturePin addMetaDataToFollowCache(IpfsFile cid)
	{
		return _cache.addToFollowCache(HighLevelCache.Type.METADATA, cid);
	}
	@Override
	public void removeMetaDataFromFollowCache(IpfsFile cid)
	{
		_cache.removeFromFollowCache(HighLevelCache.Type.METADATA, cid);
	}
	@Override
	public FuturePin addFileToFollowCache(IpfsFile cid)
	{
		return _cache.addToFollowCache(HighLevelCache.Type.FILE, cid);
	}
	@Override
	public void removeFileFromFollowCache(IpfsFile cid)
	{
		_cache.removeFromFollowCache(HighLevelCache.Type.FILE, cid);
	}
	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, Function<byte[], R> decoder)
	{
		return _cache.loadCached(file, decoder);
	}
}
