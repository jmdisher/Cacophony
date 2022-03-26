package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public class RemoteActions
{
	/**
	 * Loads a RemoteActions abstraction using the given executor, loaded from the given ILocalActions abstraction.
	 * 
	 * @param environment The environment in which this should run.
	 * @param ipfs The IPFS connection.
	 * @param keyName The name of the key to use when publishing updates.
	 * @return The abstraction over the remote actions.
	 * @throws IpfsConnectionException Something went wrong interacting with the remote server when attaching.
	 */
	public static RemoteActions loadIpfsConfig(IEnvironment environment, IConnection ipfs, String keyName) throws IpfsConnectionException
	{
		IpfsKey publicKey = _publicKeyForName(ipfs, keyName);
		return new RemoteActions(environment, ipfs, keyName, publicKey);
	}

	private static IpfsKey _publicKeyForName(IConnection ipfs, String keyName) throws IpfsConnectionException
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


	private final IEnvironment _environment;
	private final IConnection _ipfs;
	private final String _keyName;
	private final IpfsKey _publicKey;

	private RemoteActions(IEnvironment environment, IConnection ipfs, String keyName, IpfsKey publicKey)
	{
		_environment = environment;
		_ipfs = ipfs;
		_keyName = keyName;
		_publicKey = publicKey;
	}

	public IpfsFile saveData(byte[] raw) throws IpfsConnectionException
	{
		StandardEnvironment.IOperationLog log = _environment.logOperation("Saving " + raw.length + " bytes...");
		IpfsFile file = _ipfs.storeData(new ByteArrayInputStream(raw));
		log.finish("saved: " + file.toSafeString());
		return file;
	}

	public IpfsFile saveStream(InputStream stream) throws IpfsConnectionException
	{
		StandardEnvironment.IOperationLog log = _environment.logOperation("Saving stream...");
		IpfsFile file = _ipfs.storeData(stream);
		log.finish("saved: " + file.toSafeString());
		return file;
	}

	public byte[] readData(IpfsFile indexHash) throws IpfsConnectionException
	{
		return _ipfs.loadData(indexHash);
	}

	public IpfsKey getPublicKey()
	{
		return _publicKey;
	}

	public void publishIndex(IpfsFile indexHash) throws IpfsConnectionException
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != _keyName);
		
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		StandardEnvironment.IOperationLog log = _environment.logOperation("Publishing " + indexHash + " to " + _keyName);
		try
		{
			_ipfs.publish(_keyName, indexHash);
			log.finish("Success!");
		}
		catch (RuntimeException e)
		{
			log.finish("Failed: " + e.getLocalizedMessage());
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
		catch (IpfsConnectionException e)
		{
			// Unfortunately, this resolve will only fail with IOException for the cases of failed key resolution as
			// well as more general network problems.
			found = null;
		}
		return found;
	}

	public long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
	{
		return _ipfs.getSizeInBytes(cid);
	}
}
