package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jeffdisher.cacophony.data.local.CacheIndex;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;


public class RemoteActions
{
	public static RemoteActions loadIpfsConfig(LocalActions actions) throws IOException
	{
		CacheIndex cacheIndex = actions.loadCacheIndex();
		LocalIndex index = actions.readIndex();
		Assert.assertTrue(null != index);
		IPFS ipfs = new IPFS(index.ipfsHost());
		String keyName = index.keyName();
		IpfsKey publicKey = _publicKeyForName(ipfs, keyName);
		return new RemoteActions(cacheIndex, ipfs, keyName, publicKey);
	}

	private static IpfsKey _publicKeyForName(IPFS ipfs, String keyName) throws IOException
	{
		IpfsKey publicKey = null;
		for (KeyInfo info : ipfs.key.list())
		{
			if (keyName.equals(info.name))
			{
				Assert.assertTrue(null == publicKey);
				publicKey = new IpfsKey(info.id);
			}
		}
		Assert.assertTrue(null != publicKey);
		return publicKey;
	}


	private final CacheIndex _cacheIndex;
	private final IPFS _ipfs;
	private final String _keyName;
	private final IpfsKey _publicKey;

	private RemoteActions(CacheIndex cacheIndex, IPFS ipfs, String keyName, IpfsKey publicKey)
	{
		_cacheIndex = cacheIndex;
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
		// TODO:  Determine if this is the root hash.
		Multihash hash = nodes.get(0).hash;
		System.out.println("-saved: " + hash);
		
		// Update completed so notify the cache.
		_cacheIndex.hashWasAdded(new IpfsFile(hash));
		return hash;
	}

	public byte[] readData(IpfsFile indexHash) throws IOException
	{
		return _ipfs.cat(indexHash.cid());
	}

	public IpfsKey getPublicKey()
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
		String publishedPath = _ipfs.name.resolve(_publicKey.key());
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		Assert.assertTrue(published.equals(index58));
	}

	public IpfsFile resolvePublicKey(IpfsKey keyToResolve) throws IOException
	{
		String publishedPath = _ipfs.name.resolve(keyToResolve.key());
		String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
		return IpfsFile.fromIpfsCid(published);
	}

	public void unpin(IpfsFile cid) throws IOException
	{
		if (_cacheIndex.shouldUnpinAfterRemoving(cid))
		{
			// TODO:  Determine what to do with the result of this.
			_ipfs.pin.rm(cid.cid());
		}
	}
}
