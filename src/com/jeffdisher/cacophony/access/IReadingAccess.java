package com.jeffdisher.cacophony.access;

import java.util.List;

import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The aspects of the access design which require read access to the local storage (even if they seem network-only -
 * some network operations require reading the local storage state).
 */
public interface IReadingAccess extends AutoCloseable, IBasicNetworkOps
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
	 * NOTE:  Considers the home user given to the implementation when opened.
	 * 
	 * @return The last index which had been stored as the root (StreamIndex) and published (even if the publish didn't
	 * succeed).
	 */
	IpfsFile getLastRootElement();

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
	 * Requests that a new ConcurrentTransaction be opened, based on the scheduler and pin cache state of the receiver.
	 * This must be later committed or rolled back by calling commitTransactionPinCanges in IWritingAccess.
	 * 
	 * @return The new transaction.
	 */
	ConcurrentTransaction openConcurrentTransaction();

	/**
	 * Allows basic read-only access to the favourites data.
	 * 
	 * @return A reference to the restricted read-only interface to the favourites data projection.
	 */
	IFavouritesReading readableFavouritesCache();

	/**
	 * @return The list of tuples to describe the home users (can be empty).
	 */
	List<HomeUserTuple> readHomeUserData();

	/**
	 * Requests that the given indexRoot be published under our key, to the IPFS network.
	 * 
	 * @param indexRoot The root to publish.
	 * @return The asynchronous publish operation.
	 */
	FuturePublish beginIndexPublish(IpfsFile indexRoot);


	record HomeUserTuple(String keyName, IpfsKey publicKey, IpfsFile lastRoot) {}
}
