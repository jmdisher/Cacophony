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

	/**
	 * Pins the given file on the node.  Note that it isn't expected to be pinned when this is called as pinning is not
	 * a counting concept, but a boolean state.  Any higher-level pin-counting mechanism should be built on top of this,
	 * not inside it.
	 * Note that a new data element stored or existing element pinned is considered "pinned" on the node.
	 * 
	 * @param cid The file to pin.
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	void pin(IpfsFile cid) throws IpfsConnectionException;

	/**
	 * Removes the file from the node by unpinning it.  This will allow the node to reclaim the space, whether it was
	 * cached due to being directly uploaded or previously pinned.
	 * 
	 * @param cid The file to unpin.
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	void rm(IpfsFile cid) throws IpfsConnectionException;
}
