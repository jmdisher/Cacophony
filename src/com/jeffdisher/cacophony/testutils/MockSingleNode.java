package com.jeffdisher.cacophony.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.cid.Cid;


/**
 * A simple representation of a single node in the IPFS network, for purposes of testing.
 * These nodes can be connected to each other in order to test arbitrarily complex systems.
 */
public class MockSingleNode implements IConnection
{
	/**
	 * A helper to generate a testing hash for the given data.  Note that this is not the same as the IPFS hash
	 * algorithm.
	 * 
	 * @param data The data to hash.
	 * @return A hash suitable for testing.
	 */
	public static IpfsFile generateHash(byte[] data)
	{
		int hashCode = Arrays.hashCode(data);
		byte[] hash = new byte[34];
		ByteBuffer buffer = ByteBuffer.wrap(hash);
		buffer.put((byte)18).put((byte)32);
		buffer.putInt(hashCode).putInt(hashCode).putInt(hashCode).putInt(hashCode);
		return IpfsFile.fromIpfsCid(Cid.cast(hash).toString());
	}


	private final Map<String, IpfsKey> _keys;
	private final Map<IpfsKey, IpfsFile> _publications;
	private final Map<IpfsFile, byte[]> _data;
	private final MockSwarm _swarm;

	public MockSingleNode(MockSwarm swarm)
	{
		_keys = new HashMap<>();
		_publications = new HashMap<>();
		_data = new HashMap<>();
		_swarm = swarm;
		
		_swarm.registerInSwarm(this);
	}

	public void addNewKey(String keyName, IpfsKey key)
	{
		Assert.assertTrue(!_keys.containsKey(keyName));
		_keys.put(keyName, key);
	}

	public boolean isPinned(IpfsFile file)
	{
		return _data.containsKey(file);
	}

	public void timeoutKey(IpfsKey publicKey)
	{
		Assert.assertTrue(_publications.containsKey(publicKey));
		_publications.remove(publicKey);
	}

	public byte[] loadDataFromNode(IpfsFile file)
	{
		return _data.get(file);
	}

	public Set<IpfsFile> getStoredFileSet()
	{
		return Set.copyOf(_data.keySet());
	}

	@Override
	public List<Key> getKeys() throws IpfsConnectionException
	{
		return _keys.entrySet().stream().map(
				(Map.Entry<String, IpfsKey> entry) -> new IConnection.Key(entry.getKey(), entry.getValue())
		).collect(Collectors.toList());
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException
	{
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
		_data.put(newFile, data);
		return newFile;
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IpfsConnectionException
	{
		return _networkLoadData(file);
	}

	@Override
	public void publish(String keyName, IpfsKey publicKey, IpfsFile file) throws IpfsConnectionException
	{
		IpfsKey key = _keys.get(keyName);
		Assert.assertTrue(publicKey.equals(key));
		_publications.put(key, file);
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IpfsConnectionException
	{
		IpfsFile file = null;
		for (MockSingleNode node : _swarm.getNodes())
		{
			file = node._singleNodeResolve(key);
			if (null != file)
			{
				break;
			}
		}
		// We are expected to only fail with exception, never null.
		if (null == file)
		{
			throw new IpfsConnectionException("resolve", key, null);
		}
		return file;
	}

	@Override
	public long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
	{
		byte[] data = _networkLoadData(cid);
		if (null == data)
		{
			// Missing this would appear as a timeout.
			throw new IpfsConnectionException("size", this, null);
		}
		return (long) data.length;
	}

	@Override
	public String urlForDirectFetch(IpfsFile cid)
	{
		try
		{
			return new URL("http", "test", "/" + cid.toSafeString()).toString();
		}
		catch (MalformedURLException e)
		{
			throw Assert.unexpected(e);
		}
	}

	@Override
	public void pin(IpfsFile cid) throws IpfsConnectionException
	{
		// If we were told to pin this, we shouldn't already have it locally.
		Assert.assertTrue(!_data.containsKey(cid));
		// Find the data.
		byte[] data = _networkLoadData(cid);
		// If we don't see the data, we will synthesize the exception the user would normally see (it would appear as a timeout).
		if (null == data)
		{
			throw new IpfsConnectionException("pin", cid, null);
		}
		_data.put(cid, data);
	}

	@Override
	public void rm(IpfsFile cid) throws IpfsConnectionException
	{
		// Note that we will treat the cases where this wasn't found as benign.
		_data.remove(cid);
	}

	@Override
	public Key generateKey(String keyName) throws IpfsConnectionException
	{
		// We don't expect this in tests.
		throw Assert.unreachable();
	}

	@Override
	public void requestStorageGc() throws IpfsConnectionException
	{
		// Does nothing.
	}

	@Override
	public String directFetchUrlRoot()
	{
		return "http://test/";
	}


	private byte[] _networkLoadData(IpfsFile file)
	{
		byte[] data = null;
		for (MockSingleNode node : _swarm.getNodes())
		{
			data = node._singleNodeLoadData(file);
			if (null != data)
			{
				break;
			}
		}
		return data;
	}

	private byte[] _singleNodeLoadData(IpfsFile file)
	{
		return _data.get(file);
	}

	private IpfsFile _singleNodeResolve(IpfsKey key) throws IpfsConnectionException
	{
		return _publications.get(key);
	}
}
