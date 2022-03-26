package com.jeffdisher.cacophony.testutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.IPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public class MockLocalActions implements ILocalActions
{
	private final String _ipfsHost;
	private final String _keyName;
	private final IpfsFile _publishedHash;
	private final MockConnection _sharedConnection;
	private final GlobalPinCache _pinCache;
	private final IPinMechanism _pinMechanism;
	private final FollowIndex _followIndex;

	private LocalIndex _storedIndex;
	private GlobalPrefs _prefs;

	public MockLocalActions(String ipfsHost, String keyName, IpfsFile publishedHash, MockConnection sharedConnection, IPinMechanism pinMechanism)
	{
		_ipfsHost = ipfsHost;
		_keyName = keyName;
		_publishedHash = publishedHash;
		_sharedConnection = sharedConnection;
		_pinCache = GlobalPinCache.newCache();
		_pinMechanism = pinMechanism;
		_followIndex = FollowIndex.emptyFollowIndex();
		// For our tests, we specify a smaller maximum cache size (100 bytes) so that we can test it being constrained.
		_prefs = new GlobalPrefs(GlobalPrefs.defaultPrefs().videoEdgePixelMax(), 100L);
	}

	@Override
	public LocalIndex createEmptyIndex(String ipfsConnectionString, String keyName) throws UsageException
	{
		if (null != _storedIndex)
		{
			throw new UsageException("");
		}
		else
		{
			// If this path is taken, we should not have received initial data.
			Assert.assertTrue(null == _ipfsHost);
			_storedIndex = new LocalIndex(ipfsConnectionString, keyName, null);
		}
		return _storedIndex;
	}

	@Override
	public LocalIndex readExistingSharedIndex() throws UsageException
	{
		if (null == _storedIndex)
		{
			if (null != _ipfsHost)
			{
				_storedIndex = new LocalIndex(_ipfsHost, _keyName, _publishedHash);
			}
			else
			{
				throw new UsageException("");
			}
		}
		return _storedIndex;
	}

	@Override
	public void storeSharedIndex(LocalIndex localIndex)
	{
		// Verify that the serialization/deserialization of LocalIndex works when storing this.
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try
		{
			ObjectOutputStream outStream = new ObjectOutputStream(bytes);
			outStream.writeObject(localIndex);
			outStream.close();
			_storedIndex = (LocalIndex)(new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject());
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		catch (ClassNotFoundException e)
		{
			throw Assert.unexpected(e);
		}
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
		return _prefs;
	}

	@Override
	public void storePrefs(GlobalPrefs prefs)
	{
		_prefs = prefs;
	}

	@Override
	public GlobalPinCache loadGlobalPinCache()
	{
		return _pinCache;
	}

	@Override
	public String getConfigDirectoryFullPath()
	{
		return "SYNTHETIC";
	}

	public LocalIndex getStoredIndex()
	{
		return _storedIndex;
	}
}
