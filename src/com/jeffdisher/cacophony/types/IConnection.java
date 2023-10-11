package com.jeffdisher.cacophony.types;

import java.io.InputStream;
import java.util.Map;


/**
 * The abstract interface sitting on top of the IPFS connection, allowing for local testing.
 */
public interface IConnection
{
	/**
	 * Writes data from the given dataStream to the local IPFS node, returning the CID created.
	 * Note that the implementation does NOT close the dataStream.
	 * 
	 * @param dataStream The data stream to write to the local node.  NOT closed by the caller.
	 * @return The CID of the written data.
	 * @throws IpfsConnectionException If an error was encountered when attempting the upload.
	 */
	IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException;

	/**
	 * Loads the data from the local node with the given file CID.
	 * 
	 * @param file The CID to read.
	 * @return All the data from the given file.
	 * @throws IpfsConnectionException If an error was encountered when attempting the load.
	 */
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

	/**
	 * Looks up the size of the given CID.
	 * 
	 * @param cid The CID to check.
	 * @return The size of the contents of the CID, in bytes.
	 * @throws IpfsConnectionException If an error was encountered when attempting to read the data (usually couldn't be
	 * found).
	 */
	long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException;

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
	 * Gets the map of keys which exist on the local node, resolved by their names.
	 * 
	 * @return The map of key names to public keys (never null).
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	Map<String, IpfsKey> getLocalPublicKeys() throws IpfsConnectionException;

	/**
	 * Generates a new key for the given name.
	 * 
	 * @param keyName The name of the key.
	 * @return The generated key (never null).
	 * @throws IpfsConnectionException If there is some problem contacting the server or the key already exists.
	 */
	IpfsKey generateLocalPublicKey(String keyName) throws IpfsConnectionException;

	/**
	 * Deletes the named public key from the local node.
	 * 
	 * @param keyName The name of the key to delete.
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	void deletePublicKey(String keyName) throws IpfsConnectionException;

	/**
	 * Checks the status string of the IPFS daemon (data returned as JSON).
	 * 
	 * @return The status string from the IPFS daemon.
	 * @throws IpfsConnectionException If there is some problem contacting the server.
	 */
	String getIpfsStatus() throws IpfsConnectionException;
}
