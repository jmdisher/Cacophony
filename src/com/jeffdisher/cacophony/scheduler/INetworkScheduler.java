package com.jeffdisher.cacophony.scheduler;

import java.io.InputStream;

import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The interface for scheduling network-bound operations.
 * The design behind this is to allow asynchronous operation scheduling, returning futures instead of blocking.
 * An implementation may be inline, on a background thread, or some other kind of asynchronous executor or thread pool.
 */
public interface INetworkScheduler
{
	/**
	 * Reads a file from the network and decodes its data.
	 * 
	 * @param <R> The decoded data type.
	 * @param file The file to read.
	 * @param decoder The decoder to run on the returned bytes (note that the caller cannot assume what thread will run
	 * this).
	 * @return The asynchronously-completed future.
	 */
	<R> FutureRead<R> readData(IpfsFile file, DataDeserializer<R> decoder);

	/**
	 * Reads a file from the network and decodes its data, first checking that it isn't larger than a given size.
	 * It typically makes sense to use this helper, as opposed to checking size and decoding in 2 steps, as this can
	 * perform both without rescheduling and avoids all of this logic in the caller.
	 * 
	 * @param <R> The decoded data type.
	 * @param file The file to read.
	 * @param context The name to use to describe this, if there is an error.
	 * @param maxSizeInBytes The maximum size of the resource, in bytes, in order for it to be loaded (must be positive).
	 * @param decoder The decoder to run on the returned bytes (note that the caller cannot assume what thread will run
	 * this).
	 * @return The asynchronously-completed future.
	 */
	<R> FutureSizedRead<R> readDataWithSizeCheck(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder);

	/**
	 * Saves a stream of data to the network and returns the location.  Closes the stream on success or failure.
	 * 
	 * @param stream The source of the data to save.
	 * @return The asynchronously-completed future.
	 */
	FutureSave saveStream(InputStream stream);

	/**
	 * Publishes the given indexHash for this channel's key.
	 * 
	 * @param keyName The name of the key, as known to the IPFS node.
	 * @param publicKey The actual public key of this user (used for validation).
	 * @param indexHash The file to publish.
	 * @return The asynchronously-completed future.
	 */
	FuturePublish publishIndex(String keyName, IpfsKey publicKey, IpfsFile indexHash);

	/**
	 * Resolves the given keyToResolve as a public key to see the file it has published.
	 * 
	 * @param keyToResolve The key to resolve.
	 * @return The asynchronously-completed future.
	 */
	FutureResolve resolvePublicKey(IpfsKey keyToResolve);

	/**
	 * Reads the size of a file from the network.
	 * 
	 * @param cid The file to look up.
	 * @return The asynchronously-completed future.
	 */
	FutureSize getSizeInBytes(IpfsFile cid);

	/**
	 * Pins the given cid on the node.
	 * 
	 * @param cid The file to pin.
	 * @return The asynchronously-completed future.
	 */
	FuturePin pin(IpfsFile cid);

	/**
	 * Unpins the given cid on the node.
	 * 
	 * @param cid The file to unpin.
	 * @return The asynchronously-completed future.
	 */
	FutureVoid unpin(IpfsFile cid);

	/**
	 * Looks up or creates a new key on the local node.
	 * 
	 * @param keyName The name of the key.
	 * @return The asynchronously-completed future.
	 */
	FutureKey getOrCreatePublicKey(String keyName);

	/**
	 * Deletes the public key with the given name from the local node.
	 * 
	 * @param keyName The name of the key.
	 * @return The asynchronously-completed future.
	 */
	FutureVoid deletePublicKey(String keyName);
}
