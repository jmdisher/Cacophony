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
	 * Note that this can easily fail since IPNS publication is often is very slow.  As a result, a failure here is
	 * generally "safe".
	 * 
	 * @param keyName The name of the key, as known to the IPFS node.
	 * @param publicKey The actual public key of this user (used for validation).
	 * @param indexHash The index to publish for this channel's public key.
	 * @throws IpfsConnectionException If an error was encountered when attempting to publish.
	 */
	public void publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash) throws IpfsConnectionException
	{
		Assert.assertTrue(null != _ipfs);
		Assert.assertTrue(null != keyName);
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != indexHash);
		
		_ipfs.publish(keyName, publicKey, indexHash);
	}

	/**
	 * Returns the file published by the given key.
	 * 
	 * @param keyToResolve The public key to resolve.
	 * @return The published file (throws exception on failed resolution of well-formed key).
	 */
	public IpfsFile resolvePublicKey(IpfsKey keyToResolve) throws IpfsConnectionException
	{
		Assert.assertTrue(null != keyToResolve);
		return _ipfs.resolve(keyToResolve);
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
