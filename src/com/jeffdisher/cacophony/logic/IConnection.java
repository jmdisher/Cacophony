package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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

	List<Key> getKeys() throws IOException;

	IpfsFile storeData(InputStream dataStream) throws IOException;

	byte[] loadData(IpfsFile file) throws IOException;

	void publish(String keyName, IpfsFile file) throws IOException;

	IpfsFile resolve(IpfsKey key) throws IOException;

	long getSizeInBytes(IpfsFile cid);
}
