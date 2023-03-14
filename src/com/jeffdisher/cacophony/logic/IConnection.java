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

	void publish(String keyName, IpfsKey publicKey, IpfsFile file) throws IpfsConnectionException;

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

	/**
	 * Generates the given key on the IPFS node.
	 * 
	 * @param keyName The name of the key.
	 * @return The generated key (never null).
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	Key generateKey(String keyName) throws IpfsConnectionException;

	/**
	 * Requests that the remote node reclaim any storage it can.
	 * 
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	void requestStorageGc() throws IpfsConnectionException;

	/**
	 * Very similar to urlForDirectFetch() but is more like a config-level concern, as opposed to something which
	 * validates the data.  An resource of unknown existence can be opened by appending its CID directly to this string.
	 * 
	 * @return The base URL component of resources on this IPFS node.
	 */
	String directFetchUrlRoot();
}
