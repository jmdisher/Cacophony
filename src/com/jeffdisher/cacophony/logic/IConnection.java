package com.jeffdisher.cacophony.logic;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The abstract interface sitting on top of the IPFS connection, allowing for local testing.
 */
public interface IConnection
{
	static record Key(String name, IpfsKey key)
	{
	}

	List<Key> getKeys() throws IpfsConnectionException;

	IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException;

	byte[] loadData(IpfsFile file) throws IpfsConnectionException;

	void publish(String keyName, IpfsFile file) throws IpfsConnectionException;

	IpfsFile resolve(IpfsKey key) throws IpfsConnectionException;

	long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException;

	URL urlForDirectFetch(IpfsFile cid);
}
