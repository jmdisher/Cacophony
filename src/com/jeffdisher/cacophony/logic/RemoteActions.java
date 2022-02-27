package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public class RemoteActions
{
	public static RemoteActions loadIpfsConfig(ILocalActions local) throws IOException
	{
		LocalIndex index = local.readIndex();
		Assert.assertTrue(null != index);
		IConnection ipfs = local.getSharedConnection();
		String keyName = index.keyName();
		IpfsKey publicKey = _publicKeyForName(ipfs, keyName);
		return new RemoteActions(ipfs, keyName, publicKey);
	}

	private static IpfsKey _publicKeyForName(IConnection ipfs, String keyName) throws IOException
	{
		IpfsKey publicKey = null;
		for (IConnection.Key info : ipfs.getKeys())
		{
			if (keyName.equals(info.name()))
			{
				Assert.assertTrue(null == publicKey);
				publicKey = info.key();
			}
		}
		Assert.assertTrue(null != publicKey);
		return publicKey;
	}


	private final IConnection _ipfs;
	private final String _keyName;
	private final IpfsKey _publicKey;

	private RemoteActions(IConnection ipfs, String keyName, IpfsKey publicKey)
	{
		_ipfs = ipfs;
		_keyName = keyName;
		_publicKey = publicKey;
	}

	public IpfsFile saveData(byte[] raw) throws IOException
	{
		System.out.println("Saving " + raw.length + " bytes...");
		IpfsFile file = _ipfs.storeData(new ByteArrayInputStream(raw));
		System.out.println("-saved: " + file.toSafeString());
		return file;
	}

	public IpfsFile saveStream(InputStream stream) throws IOException
	{
		IpfsFile file = _ipfs.storeData(stream);
		System.out.println("-saved: " + file.toSafeString());
		return file;
	}

	public byte[] readData(IpfsFile indexHash) throws IOException
	{
		return _ipfs.loadData(indexHash);
	}

	public IpfsKey getPublicKey()
	{
		return _publicKey;
	}

	public void publishIndex(IpfsFile indexHash) throws IOException
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != _keyName);
		
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		System.out.println("Publishing " + indexHash + " to " + _keyName);
		try
		{
			_ipfs.publish(_keyName, indexHash);
			System.out.println("-Success!");
		}
		catch (RuntimeException e)
		{
			System.out.println("-Failed: " + e.getLocalizedMessage());
		}
		
		// If we never got a normal success from the publish, we will at least still claim to have succeeded if the key has been updated on the local node.
		IpfsFile published = _ipfs.resolve(_publicKey);
		Assert.assertTrue(published.toSafeString().equals(indexHash.toSafeString()));
	}

	/**
	 * Returns the file published by the given key or null if the key is unknown or some other network error occurred
	 * contacting the IPFS node.
	 * 
	 * @param keyToResolve The public key to resolve.
	 * @return The published file or null, if the key isn't found.
	 */
	public IpfsFile resolvePublicKey(IpfsKey keyToResolve)
	{
		IpfsFile found = null;
		try
		{
			found = _ipfs.resolve(keyToResolve);
		}
		catch (IOException e)
		{
			// Unfortunately, this resolve will only fail with IOException for the cases of failed key resolution as
			// well as more general network problems.
			found = null;
		}
		return found;
	}

	public long getSizeInBytes(IpfsFile cid)
	{
		return _ipfs.getSizeInBytes(cid);
	}
}
