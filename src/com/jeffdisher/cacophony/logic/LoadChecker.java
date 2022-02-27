package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This helper class just exists to allow us to verify that our cache assumptions are correct when loading data from
 * the network.
 * Currently, we do have some operations which don't require cached data (commands used in discovering new users) but
 * we will eventually want to force even those cases through an "explicit cache".
 */
public class LoadChecker
{
	private final RemoteActions _remote;
	private final GlobalPinCache _cache;

	public LoadChecker(RemoteActions remote, ILocalActions local)
	{
		_remote = remote;
		_cache = local.loadGlobalPinCache();
	}

	public byte[] loadCached(IpfsFile file) throws IOException
	{
		Assert.assertTrue(_cache.isCached(file));
		return _remote.readData(file);
	}
}
