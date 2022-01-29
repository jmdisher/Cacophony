package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class MockConnection implements IConnection
{
	private final String _keyName;
	private final IpfsKey _key;
	private final Map<IpfsFile, byte[]> _dataStore;

	private IpfsFile _root;

	public MockConnection(String keyName, IpfsKey key)
	{
		_keyName = keyName;
		_key = key;
		_dataStore = new HashMap<>();
	}

	@Override
	public List<Key> getKeys() throws IOException
	{
		return Collections.singletonList(new IConnection.Key(_keyName, _key));
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IOException
	{
		return _dataStore.get(file);
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IOException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IOException
	{
		Assert.assertTrue(_key.equals(key));
		return _root;
	}

	public void setRootForKey(IpfsKey key, IpfsFile root)
	{
		Assert.assertTrue(_key.equals(key));
		_root = root;
	}

	public void storeData(IpfsFile file, byte[] data)
	{
		_dataStore.put(file, data);
	}
}
