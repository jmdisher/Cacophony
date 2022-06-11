package com.jeffdisher.cacophony.scheduler;

import java.io.InputStream;
import java.util.function.Function;

import com.jeffdisher.cacophony.types.IpfsFile;


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
	<R> FutureRead<R> readData(IpfsFile file, Function<byte[], R> decoder);

	/**
	 * Saves a stream of data to the network and returns the location.
	 * 
	 * @param stream The source of the data to save.
	 * @param shouldCloseStream True if the stream should be closed after the save completes (on success or failure).
	 * @return The asynchronously-completed future.
	 */
	FutureSave saveStream(InputStream stream, boolean shouldCloseStream);
}