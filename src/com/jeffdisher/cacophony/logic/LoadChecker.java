package com.jeffdisher.cacophony.logic;

import java.net.URL;

import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
	private final ILocalActions _local;
	private final GlobalPinCache _cache;

	public LoadChecker(RemoteActions remote, ILocalActions local)
	{
		_remote = remote;
		_local = local;
		_cache = local.loadGlobalPinCache();
	}

	public byte[] loadCached(IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_cache.isCached(file));
		return _remote.readData(file);
	}

	public byte[] loadNotCached(IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		// Note that we don't want to assert here, since there can be hash collisions (empty structures are common) but
		// we do want to at least log a warning here, just so we are aware of it in tests.
		if (_cache.isCached(file))
		{
			System.err.println("WARNING!  Not expected in cache:  " + file);
		}
		return _remote.readData(file);
	}

	public URL getCachedUrl(IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_cache.isCached(file));
		return _local.getSharedConnection().urlForDirectFetch(file);
	}

	public boolean isCached(IpfsFile file)
	{
		return _cache.isCached(file);
	}
}
