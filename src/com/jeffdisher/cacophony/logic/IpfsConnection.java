package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
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
		Assert.assertTrue(1 == nodes.size());
		// TODO:  Determine if this is the root hash.
		Multihash hash = nodes.get(0).hash;
		
		return new IpfsFile(hash);
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IOException
	{
		return _connection.cat(file.cid());
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IOException
	{
		Map<?,?> map = _connection.name.publish(file.cid(), Optional.of(keyName));
		String value = (String) map.get("Value");
		String index58 = file.cid().toString();
		Assert.assertTrue(value.substring(value.lastIndexOf("/") + 1).equals(index58));
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IOException
	{
		String publishedPath = _connection.name.resolve(key.key());
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		return IpfsFile.fromIpfsCid(published);
	}
}
