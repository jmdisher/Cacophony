package com.jeffdisher.cacophony.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import io.ipfs.cid.Cid;


public class MockConnection implements IConnection
{
	public static IpfsFile generateHash(byte[] data)
	{
		int hashCode = Arrays.hashCode(data);
		byte[] hash = new byte[34];
		ByteBuffer buffer = ByteBuffer.wrap(hash);
		buffer.put((byte)18).put((byte)32);
		buffer.putInt(hashCode).putInt(hashCode).putInt(hashCode).putInt(hashCode);
		return IpfsFile.fromIpfsCid(Cid.cast(hash).toString());
	}


	private final String _keyName;
	private final IpfsKey _key;
	private final MockPinMechanism _pinMechanism;
	private final MockConnection _peer;
	private final Map<IpfsFile, byte[]> _dataStore;

	private IpfsFile _root;

	public MockConnection(String keyName, IpfsKey key, MockPinMechanism pinMechanism, MockConnection peer)
	{
		_keyName = keyName;
		_key = key;
		_pinMechanism = pinMechanism;
		_peer = peer;
		_dataStore = new HashMap<>();
		
		_pinMechanism.attachRemoteIngest((IpfsFile cid, byte[] data) -> {
			Assert.assertTrue(!_dataStore.containsKey(cid));
			_dataStore.put(cid, data);
			return null;
		});
	}

	@Override
	public List<Key> getKeys() throws IOException
	{
		return Collections.singletonList(new IConnection.Key(_keyName, _key));
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IOException
	{
		// We will emulate this hash using a Java hashcode of the bytes.
		byte[] data = dataStream.readAllBytes();
		IpfsFile newFile = generateHash(data);
		_storeData(newFile, data);
		return newFile;
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IOException
	{
		// We will only load the data if it is pinned to emulate the impacts of unpinning.
		return _pinMechanism.isPinned(file)
				? _dataStore.get(file)
				: null
		;
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IOException
	{
		Assert.assertTrue(_keyName.equals(keyName));
		_root = file;
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IOException
	{
		return (_key.equals(key))
				? _root
				: _pinMechanism.remoteResolve(key)
		;
	}

	@Override
	public long getSizeInBytes(IpfsFile cid)
	{
		return _pinMechanism.isPinned(cid)
				? (long) _dataStore.get(cid).length
				: _peer.getSizeInBytes(cid)
		;
	}

	public void setRootForKey(IpfsKey key, IpfsFile root)
	{
		Assert.assertTrue(_key.equals(key));
		_root = root;
	}

	public void storeData(IpfsFile file, byte[] data)
	{
		_storeData(file, data);
	}


	private void _storeData(IpfsFile file, byte[] data)
	{
		_dataStore.put(file, data);
		
		// We will tell the pin mechanism that this counts as pinned since it is stored.
		_pinMechanism.addLocalFile(file);
	}

	@Override
	public URL urlForDirectFetch(IpfsFile cid)
	{
		try
		{
			return new URL("http", "test", "/" + cid.toSafeString());
		}
		catch (MalformedURLException e)
		{
			throw new RuntimeException(e);
		}
	}
}
