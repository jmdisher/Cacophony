package com.jeffdisher.cacophony.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

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
	private final MockConnection _peer;
	private final Map<IpfsFile, byte[]> _dataStore;
	private final Set<IpfsFile> _pinned;
	private final BiFunction<IpfsFile, byte[], Void> _pinIngestor;

	private IpfsFile _root;

	public MockConnection(String keyName, IpfsKey key, MockConnection peer)
	{
		_keyName = keyName;
		_key = key;
		_peer = peer;
		_dataStore = new HashMap<>();
		_pinned = new HashSet<>();
		_pinIngestor = (IpfsFile cid, byte[] data) -> {
			Assert.assertTrue(!_dataStore.containsKey(cid));
			_dataStore.put(cid, data);
			return null;
		};
	}

	@Override
	public List<Key> getKeys() throws IpfsConnectionException
	{
		return Collections.singletonList(new IConnection.Key(_keyName, _key));
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException
	{
		// We will emulate this hash using a Java hashcode of the bytes.
		byte[] data;
		try
		{
			data = dataStream.readAllBytes();
		}
		catch (IOException e)
		{
			// Not expected in tests.
			throw Assert.unexpected(e);
		}
		IpfsFile newFile = generateHash(data);
		_storeData(newFile, data);
		return newFile;
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IpfsConnectionException
	{
		// We will only load the data if it is pinned to emulate the impacts of unpinning.
		return _pinned.contains(file)
				? _dataStore.get(file)
				: null
		;
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IpfsConnectionException
	{
		Assert.assertTrue(_keyName.equals(keyName));
		_root = file;
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IpfsConnectionException
	{
		return (_key.equals(key))
				? _root
				: _remoteResolve(key)
		;
	}

	@Override
	public long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
	{
		long size = -1l;
		if (_pinned.contains(cid))
		{
			size = (long) _dataStore.get(cid).length;
		}
		else if (null != _peer)
		{
			size = _peer.getSizeInBytes(cid);
		}
		else
		{
			// This is effectively a network timeout.
			throw new IpfsConnectionException("size", cid, new IOException("File not found"));
		}
		return size;
	}

	public void setRootForKey(IpfsKey key, IpfsFile root)
	{
		Assert.assertTrue(_key.equals(key));
		_root = root;
	}


	private void _storeData(IpfsFile file, byte[] data)
	{
		_dataStore.put(file, data);
		
		Assert.assertTrue(!_pinned.contains(file));
		_pinned.add(file);
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

	@Override
	public void pin(IpfsFile cid) throws IpfsConnectionException
	{
		Assert.assertTrue(!_pinned.contains(cid));
		// This path is assumed to be for remote pins (since that is why this is called) so we must have an attached peer.
		byte[] data = _peer.loadData(cid);
		// We will fail if we can't find this somewhere in the network.
		if (null == data)
		{
			throw new IpfsConnectionException("pin", cid, new IOException("File not found"));
		}
		_pinIngestor.apply(cid, data);
		_pinned.add(cid);
	}

	@Override
	public void rm(IpfsFile cid) throws IpfsConnectionException
	{
		Assert.assertTrue(_pinned.contains(cid));
		_pinned.remove(cid);
	}

	@Override
	public IConnection.Key generateKey(String keyName) throws IpfsConnectionException
	{
		// We don't expect this in the test.
		throw Assert.unreachable();
	}

	@Override
	public void requestStorageGc() throws IpfsConnectionException
	{
		// Does nothing in test.
	}

	public boolean isPinned(IpfsFile cid)
	{
		return _pinned.contains(cid);
	}

	public void deleteAndUnpinFile(IpfsFile cid)
	{
		Assert.assertTrue(_pinned.remove(cid));
		Assert.assertTrue(null != _dataStore.remove(cid));
	}


	private IpfsFile _remoteResolve(IpfsKey key) throws IpfsConnectionException
	{
		if (null != _peer)
		{
			return _peer.resolve(key);
		}
		else
		{
			// The real IPFS daemon seems to throw IOException when it can't resolve.
			throw new IpfsConnectionException("resolve", key, new IOException("Peer does not exist"));
		}
	}
}
