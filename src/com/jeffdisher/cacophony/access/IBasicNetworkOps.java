package com.jeffdisher.cacophony.access;

import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This interface was extracted from IReadingAccess purely to so that ConcurrentTransaction could implement so that
 * these could be used, interchangeably, in ForeignChannelReader.
 */
public interface IBasicNetworkOps
{
	/**
	 * Loads the given file, decoding it into and instance of R, but asserting that it is pinned on the local node.
	 * 
	 * @param <R> The type to ultimately return.
	 * @param file The file to load.
	 * @param decoder The decoder helper to invoke on the returned bytes.
	 * @return The loaded and decoded instance.
	 */
	<R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder);

	/**
	 * Loads the given file, first checking it isn't too big, decoding it into and instance of R, but assuming that it
	 * is NOT pinned on the local node.
	 * 
	 * @param <R> The type to ultimately return.
	 * @param file The file to load.
	 * @param context The name to use to describe this, if there is an error.
	 * @param maxSizeInBytes The maximum size of the resource, in bytes, in order for it to be loaded (must be positive).
	 * @param decoder The decoder helper to invoke on the returned bytes.
	 * @return The loaded and decoded instance.
	 */
	<R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder);
}
