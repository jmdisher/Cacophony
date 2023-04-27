package com.jeffdisher.cacophony.logic;

import java.io.InputStream;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The abstract interface sitting on top of the IPFS connection, allowing for local testing.
 */
public interface IConnection
{
	IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException;

	byte[] loadData(IpfsFile file) throws IpfsConnectionException;

	/**
	 * Publishes the given file for this channel's public key.
	 * Note that this can easily fail since IPNS publication is often is very slow.  As a result, a failure here is
	 * generally "safe".
	 * 
	 * @param keyName The name of the key, as known to the IPFS node.
	 * @param publicKey The actual public key of this user (used for validation).
	 * @param file The file to publish for this channel's public key.
	 * @throws IpfsConnectionException If an error was encountered when attempting to publish.
	 */
	void publish(String keyName, IpfsKey publicKey, IpfsFile file) throws IpfsConnectionException;

	/**
	 * Returns the file published by the given key.
	 * 
	 * @param key The public key to resolve.
	 * @return The published file (throws exception on failed resolution of well-formed key).
	 */
	IpfsFile resolve(IpfsKey key) throws IpfsConnectionException;

	long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException;

	/**
	 * Gets the URL for directly fetching this resource from the IPFS node.
	 * 
	 * @param cid The resource.
	 * @return The URL.
	 */
	String urlForDirectFetch(IpfsFile cid);

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

	/**
	 * Looks up a named key on the local IPFS node, generating it if it doesn't exist.
	 * 
	 * @param keyName The name of the key.
	 * @return The generated key (never null).
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	IpfsKey getOrCreatePublicKey(String keyName) throws IpfsConnectionException;
}
