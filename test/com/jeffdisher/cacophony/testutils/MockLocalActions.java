package com.jeffdisher.cacophony.testutils;

import org.junit.Assert;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.IPinMechanism;


public class MockLocalActions implements ILocalActions
{
	private final String _ipfsHost;
	private final String _keyName;
	private final MockConnection _sharedConnection;
	private final GlobalPinCache _pinCache;
	private final IPinMechanism _pinMechanism;
	private final FollowIndex _followIndex;

	private LocalIndex _storedIndex;

	public MockLocalActions(String ipfsHost, String keyName, MockConnection sharedConnection, GlobalPinCache pinCache, IPinMechanism pinMechanism, FollowIndex followIndex)
	{
		_ipfsHost = ipfsHost;
		_keyName = keyName;
		_sharedConnection = sharedConnection;
		_pinCache = pinCache;
		_pinMechanism = pinMechanism;
		_followIndex = followIndex;
	}

	@Override
	public LocalIndex readIndex()
	{
		return (null != _storedIndex)
				? _storedIndex
				: ((null != _ipfsHost)
					? new LocalIndex(_ipfsHost, _keyName)
					: null
				)
		;
	}

	@Override
	public void storeIndex(LocalIndex index)
	{
		Assert.assertTrue(null == _storedIndex);
		_storedIndex = index;
	}

	@Override
	public IConnection getSharedConnection()
	{
		return _sharedConnection;
	}

	@Override
	public IPinMechanism getSharedPinMechanism()
	{
		return _pinMechanism;
	}

	@Override
	public FollowIndex loadFollowIndex()
	{
		return _followIndex;
	}

	@Override
	public GlobalPrefs readPrefs()
	{
		// For our tests, we specify a smaller maximum cache size (100 bytes) so that we can test it being constrained.
		return new GlobalPrefs(GlobalPrefs.defaultPrefs().videoEdgePixelMax(), 100L);
	}

	@Override
	public GlobalPinCache loadGlobalPinCache()
	{
		return _pinCache;
	}

	public LocalIndex getStoredIndex()
	{
		return _storedIndex;
	}
}
