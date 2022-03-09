package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;


public class IpfsConnection implements IConnection
{
	private final IPFS _connection;

	public IpfsConnection(IPFS connection)
	{
		_connection = connection;
	}

	@Override
	public List<Key> getKeys() throws IOException
	{
		return _connection.key.list().stream().map((info) -> new IConnection.Key(info.name, new IpfsKey(info.id))).collect(Collectors.toList());
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IOException
	{
		NamedStreamable.InputStreamWrapper wrapper = new NamedStreamable.InputStreamWrapper(dataStream);
		
		List<MerkleNode> nodes = _connection.add(wrapper);
		// Even with larger files, this only returns a single element, so it isn't obvious why this is a list.
		Assert.assertTrue(1 == nodes.size());
		Multihash hash = nodes.get(0).hash;
		
		return new IpfsFile(hash);
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IOException
	{
		return _connection.cat(file.getMultihash());
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IOException
	{
		Map<?,?> map = _connection.name.publish(file.getMultihash(), Optional.of(keyName));
		String value = (String) map.get("Value");
		String index58 = file.toSafeString();
		Assert.assertTrue(value.substring(value.lastIndexOf("/") + 1).equals(index58));
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IOException
	{
		String publishedPath = _connection.name.resolve(key.getMultihash());
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		return IpfsFile.fromIpfsCid(published);
	}

	@Override
	public long getSizeInBytes(IpfsFile cid)
	{
		try
		{
			Map<?,?> dataMap = _connection.file.ls(cid.getMultihash());
			return ((Number)((Map<?,?>)((Map<?,?>)dataMap.get("Objects")).get(cid.toSafeString())).get("Size")).longValue();
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}

	@Override
	public URL urlForDirectFetch(IpfsFile cid)
	{
		try
		{
			return new URL(_connection.protocol, _connection.host, _connection.port, "/ipfs/" + cid.toSafeString());
		}
		catch (MalformedURLException e)
		{
			throw Assert.unexpected(e);
		}
	}
}
