package com.jeffdisher.cacophony.access;

import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The aspects of the access design which require read access to the local storage (even if they seem network-only -
 * some network operations require reading the local storage state).
 */
public interface IReadingAccess extends AutoCloseable
{
	/**
	 * We implement AudoCloseable so we can use the try-with-resources idiom but we have no need for the exception so
	 * we override the close() not to throw it.
	 */
	void close();

	/**
	 * Allows basic read-only access to the followee data, since that is a common use-case.
	 * 
	 * @return A reference to the restricted read-only interface to the followee data projection.
	 */
	IFolloweeReading readableFolloweeData();

	/**
	 * Checks if the given file is tracked in the local pin cache.
	 * NOTE:  This should only be used in tests as it doesn't have any general use (it is too low-level).
	 * 
	 * @param file The file to check.
	 * @return True if this file is in the pin cache, false if it is not explicitly pinned locally.
	 */
	boolean isInPinCached(IpfsFile file);

	/**
	 * @return The preferences for this channel.
	 */
	PrefsData readPrefs();

	/**
	 * Requests that the IPFS node reclaim storage.
	 * @throws IpfsConnectionException There was an error connecting to IPFS.
	 */
	void requestIpfsGc() throws IpfsConnectionException;

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

	/**
	 * Gets a URL which can be used to access the given file.
	 * 
	 * @param file A file on the IPFS node.
	 * @return The URL to directly GET the file contents.
	 */
	String getCachedUrl(IpfsFile file);

	/**
	 * @return The last index which had been stored as the root (StreamIndex) and published (even if the publish didn't
	 * succeed).
	 */
	IpfsFile getLastRootElement();

	/**
	 * @return The public key of this channel.
	 */
	IpfsKey getPublicKey();

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
	 * Republishes the last saved root element for this channel's key.
	 * 
	 * @return The asynchronously-completed future.
	 */
	FuturePublish republishIndex();

	/**
	 * Requests that a new ConcurrentTransaction be opened, based on the scheduler and pin cache state of the receiver.
	 * This must be later committed or rolled back by calling commitTransactionPinCanges in IWritingAccess.
	 * 
	 * @return The new transaction.
	 */
	ConcurrentTransaction openConcurrentTransaction();

	/**
	 * Very similar to getCachedUrl() but is more like a config-level concern, as opposed to something which validates
	 * the data.  An resource of unknown existence can be opened by appending its CID directly to this string.
	 * 
	 * @return The base URL component of resources on this IPFS node.
	 */
	String getDirectFetchUrlRoot();
}
