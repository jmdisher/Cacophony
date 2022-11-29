package com.jeffdisher.cacophony.scheduler;

import java.io.InputStream;

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
	 * Saves a stream of data to the network and returns the location.
	 * 
	 * @param stream The source of the data to save.
	 * @param shouldCloseStream True if the stream should be closed after the save completes (on success or failure).
	 * @return The asynchronously-completed future.
	 */
	FutureSave saveStream(InputStream stream, boolean shouldCloseStream);

	/**
	 * Publishes the given indexHash for this channel's key.
	 * 
	 * @param indexHash The file to publish.
	 * @return The asynchronously-completed future.
	 */
	FuturePublish publishIndex(IpfsFile indexHash);

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
	 * This method returns immediately as the underlying network connection eagerly resolves this at startup.
	 * 
	 * @return The public key for this channel.
	 */
	IpfsKey getPublicKey();

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
	FutureUnpin unpin(IpfsFile cid);

	/**
	 * Requests that the scheduler shut down and dispose of any resources before the system goes down.
	 */
	void shutdown();
}
