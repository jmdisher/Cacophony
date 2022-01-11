package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.cid.Cid;
import io.ipfs.multihash.Multihash;


public class RemoteActions
{
	public static RemoteActions loadIpfsConfig(LocalActions actions) throws IOException
	{
		LocalIndex index = actions.readIndex();
		Assert.assertTrue(null != index);
		IPFS ipfs = new IPFS(index.ipfsHost());
		String keyName = index.keyName();
		Multihash publicKey = _publicKeyForName(ipfs, keyName);
		return new RemoteActions(ipfs, keyName, publicKey);
	}

	private static Multihash _publicKeyForName(IPFS ipfs, String keyName) throws IOException
	{
		Multihash publicKey = null;
		for (KeyInfo info : ipfs.key.list())
		{
			if (keyName.equals(info.name))
			{
				Assert.assertTrue(null == publicKey);
				publicKey = info.id;
			}
		}
		Assert.assertTrue(null != publicKey);
		return publicKey;
	}


	private final IPFS _ipfs;
	private final String _keyName;
	private final Multihash _publicKey;

	private RemoteActions(IPFS ipfs, String keyName, Multihash publicKey)
	{
		_ipfs = ipfs;
		_keyName = keyName;
		_publicKey = publicKey;
	}

	public Multihash saveData(byte[] raw) throws IOException
	{
		System.out.println("Saving " + raw.length + " bytes...");
		NamedStreamable.ByteArrayWrapper wrapper = new NamedStreamable.ByteArrayWrapper(raw);
		
		List<MerkleNode> nodes = _ipfs.add(wrapper);
		Assert.assertTrue(1 == nodes.size());
		System.out.println("-saved: " + nodes.get(0).hash);
		return nodes.get(0).hash;
	}

	public byte[] readData(Multihash indexHash) throws IOException
	{
		return _ipfs.cat(indexHash);
	}

	public Multihash getPublicKey()
	{
		return _publicKey;
	}

	public void publishIndex(Multihash indexHash) throws IOException
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != _keyName);
		
		String index58 = indexHash.toBase58();
		
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		System.out.println("Publishing " + indexHash + " to " + _keyName);
		try
		{
			Map<?,?> map = _ipfs.name.publish(indexHash, Optional.of(_keyName));
			String value = (String) map.get("Value");
			Assert.assertTrue(value.substring(value.lastIndexOf("/") + 1).equals(index58));
			System.out.println("-Success!");
		}
		catch (RuntimeException e)
		{
			System.out.println("-Failed: " + e.getLocalizedMessage());
		}
		
		// If we never got a normal success from the publish, we will at least still claim to have succeeded if the key has been updated on the local node.
		String publishedPath = _ipfs.name.resolve(_publicKey);
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		Assert.assertTrue(published.equals(index58));
	}

	public Multihash resolvePublicKey(Multihash keyToResolve) throws IOException
	{
		String publishedPath = _ipfs.name.resolve(keyToResolve);
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		return Cid.fromBase58(published);
	}

	public void unpin(Multihash cid) throws IOException
	{
		// TODO:  Determine what to do with the result of this.
		_ipfs.pin.rm(cid);
	}
}
