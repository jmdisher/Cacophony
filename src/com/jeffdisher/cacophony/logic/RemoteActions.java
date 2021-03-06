package com.jeffdisher.cacophony.logic;

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

	/**
	 * Publishes the given indexHash for this channel's public key.
	 * Note that this can easily fail since IPNS publication is often is very slow.  The failure is considered "safe"
	 * and is only logged, instead of throwing an exception.  The caller can check if it did work based on the return
	 * value.
	 * 
	 * @param indexHash The index to publish for this channel's public key.
	 * @return The exception encountered when attempting to publish, null if the publish was a success.
	 */
	public IpfsConnectionException publishIndex(IpfsFile indexHash)
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != _keyName);
		
		IpfsConnectionException error = null;
		try
		{
			_ipfs.publish(_keyName, indexHash);
			error = null;
		}
		catch (IpfsConnectionException e)
		{
			error = e;
		}
		
		if (_environment.shouldEnableVerifications())
		{
			// If we never got a normal success from the publish, we will at least still claim to have succeeded if the key has been updated on the local node.
			try
			{
				IpfsFile published = _ipfs.resolve(_publicKey);
				if (published.toSafeString().equals(indexHash.toSafeString()))
				{
					// Even if there was a timeout error, the correct answer is there so it may have completed asynchronously.
					error = null;
				}
			}
			catch (IpfsConnectionException e)
			{
				if (null == error)
				{
					error = e;
				}
			}
		}
		return error;
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
		return _getSizeInBytes(cid);
	}

	public void pin(IpfsFile cid) throws IpfsConnectionException
	{
		_ipfs.pin(cid);
	}

	public void unpin(IpfsFile cid) throws IpfsConnectionException
	{
		_ipfs.rm(cid);
	}


	private long _getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
	{
		return _ipfs.getSizeInBytes(cid);
	}
}
