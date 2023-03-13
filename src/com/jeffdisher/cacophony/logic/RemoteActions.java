package com.jeffdisher.cacophony.logic;

import java.io.InputStream;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public class RemoteActions
{
	/**
	 * Loads a RemoteActions abstraction on top of the given IConnection.
	 * 
	 * @param ipfs The IPFS connection.
	 * @return The abstraction over the remote actions.
	 */
	public static RemoteActions loadIpfsConfig(IConnection ipfs)
	{
		return new RemoteActions(ipfs);
	}


	private final IConnection _ipfs;

	private RemoteActions(IConnection ipfs)
	{
		_ipfs = ipfs;
	}

	public IpfsFile saveStream(InputStream stream) throws IpfsConnectionException
	{
		return _ipfs.storeData(stream);
	}

	public byte[] readData(IpfsFile indexHash) throws IpfsConnectionException
	{
		return _ipfs.loadData(indexHash);
	}

	/**
	 * Publishes the given indexHash for this channel's public key.
	 * Note that this can easily fail since IPNS publication is often is very slow.  The failure is considered "safe"
	 * and is only logged, instead of throwing an exception.  The caller can check if it did work based on the return
	 * value.
	 * 
	 * @param keyName The name of the key, as known to the IPFS node.
	 * @param publicKey The actual public key of this user (used for validation).
	 * @param indexHash The index to publish for this channel's public key.
	 * @return The exception encountered when attempting to publish, null if the publish was a success.
	 */
	public IpfsConnectionException publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash)
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != keyName);
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != indexHash);
		
		IpfsConnectionException error = null;
		try
		{
			_ipfs.publish(keyName, indexHash);
			error = null;
		}
		catch (IpfsConnectionException e)
		{
			error = e;
		}
		
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		if (null != error)
		{
			// If we never got a normal success from the publish, we will at least still claim to have succeeded if the key has been updated on the local node.
			try
			{
				IpfsFile published = _ipfs.resolve(publicKey);
				if (published.toSafeString().equals(indexHash.toSafeString()))
				{
					// Even if there was a timeout error, the correct answer is there so it may have completed asynchronously.
					error = null;
				}
			}
			catch (IpfsConnectionException e)
			{
				// We will ignore this since we want the original exception.
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
		Assert.assertTrue(null != keyToResolve);
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
