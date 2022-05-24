package com.jeffdisher.cacophony.logic;

import java.net.URL;

import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
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
	private final GlobalPinCache _cache;
	private final IConnection _ipfsConnection;

	public LoadChecker(RemoteActions remote, GlobalPinCache pinCache, IConnection ipfsConnection)
	{
		_remote = remote;
		_cache = pinCache;
		_ipfsConnection = ipfsConnection;
	}

	public byte[] loadCached(IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_cache.isCached(file));
		return _remote.readData(file);
	}

	public byte[] loadNotCached(IEnvironment environment, IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		// Note that we don't want to assert here, since there can be hash collisions (empty structures are common) but
		// we do want to at least log a warning here, just so we are aware of it in tests.
		if (_cache.isCached(file))
		{
			environment.logError("WARNING!  Not expected in cache:  " + file);
		}
		return _remote.readData(file);
	}

	public URL getCachedUrl(IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_cache.isCached(file));
		return _ipfsConnection.urlForDirectFetch(file);
	}

	public boolean isCached(IpfsFile file)
	{
		return _cache.isCached(file);
	}
}
