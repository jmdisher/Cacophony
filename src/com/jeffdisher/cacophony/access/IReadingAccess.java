package com.jeffdisher.cacophony.access;

import java.net.URL;
import java.util.function.Function;
import java.util.function.Supplier;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.IReadOnlyFollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSize;
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
	 * Allows basic read-only access to the FollowIndex, since that is a common use-case.
	 * 
	 * @return A reference to the restricted read-only interface to the FollowIndex.
	 */
	IReadOnlyFollowIndex readOnlyFollowIndex();

	// TEMP.
	IConnection connection() throws IpfsConnectionException;

	// TEMP.
	LocalRecordCache lazilyLoadFolloweeCache(Supplier<LocalRecordCache> cacheGenerator);

	// TEMP - only used for tests.
	boolean isInPinCached(IpfsFile file);

	/**
	 * @return The preferences for this channel.
	 */
	GlobalPrefs readGlobalPrefs();

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
	<R> FutureRead<R> loadCached(IpfsFile file, Function<byte[], R> decoder);

	/**
	 * Loads the given file, decoding it into and instance of R, but asserting that it is NOT pinned on the local node.
	 * 
	 * @param <R> The type to ultimately return.
	 * @param file The file to load.
	 * @param decoder The decoder helper to invoke on the returned bytes.
	 * @return The loaded and decoded instance.
	 */
	<R> FutureRead<R> loadNotCached(IpfsFile file, Function<byte[], R> decoder);

	/**
	 * Gets a URL which can be used to access the given file.
	 * 
	 * @param file A file on the IPFS node.
	 * @return The URL to directly GET the file contents.
	 */
	URL getCachedUrl(IpfsFile file);

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
}
