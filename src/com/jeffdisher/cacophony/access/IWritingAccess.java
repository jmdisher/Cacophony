package com.jeffdisher.cacophony.access;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The aspects of the access design which require writing to the local storage (even if they seem network-only - some
 * network operations require updates to local storage state).
 */
public interface IWritingAccess extends IReadingAccess
{
	/**
	 * Allows direct read-write access to the shared followee data projection instance.  This interface is provided for
	 * cache update and management logic.
	 * Calling this helper will mark the followee data as needing to be written-back, upon closing the access.
	 * 
	 * @return The shared followee data instance.
	 */
	IFolloweeWriting writableFolloweeData();

	/**
	 * Requests that the given prefs be written to disk.
	 * 
	 * @param prefs The new prefs object.
	 */
	void writePrefs(PrefsData prefs);

	/**
	 * Uploads the data in dataToSave, recording that the file is pinned locally.  Closes the stream on success or
	 * failure.
	 * 
	 * @param dataToSave The data stream to write to the server.
	 * @return The hash of the saved file.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	IpfsFile uploadAndPin(InputStream dataToSave) throws IpfsConnectionException;

	/**
	 * Uploads the new AbstractIndex and updates local tracking.
	 * Note that this doesn't republish the new index, as that needs to be explicitly done.
	 * 
	 * @param streamIndex The new stream index.
	 * @return The CID of the published index.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	IpfsFile uploadIndexAndUpdateTracking(AbstractIndex streamIndex) throws IpfsConnectionException;

	/**
	 * Requests that the given cid be pinned on the local node.  Since a pin operation can be a very long-running
	 * operation (either because the node is fetching a lot of data or because it timed out), the result is returned
	 * as a future.
	 * NOTE:  The implementation should merely record the duplicate pin, in the case where the file has already been
	 * pinned, since the pin actions are reference-counted.
	 * 
	 * @param cid The file to pin locally.
	 * @return The future of the pin status.
	 */
	FuturePin pin(IpfsFile cid);

	/**
	 * Requests that the given cid be unpinned on the local node.
	 * NOTE:  The implementation should interpret this as a decrement of the pin reference count, only actually
	 * unpinning from the IPFS node if this count drops to 0.
	 * 
	 * @param cid The file to unpin.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	void unpin(IpfsFile cid) throws IpfsConnectionException;

	/**
	 * Called when we wish to commit or rollback a ConcurrentTransaction.  This helper allows it to rationalize any pin
	 * state changes it performed with the actual values in the access object's pin cache.
	 * 
	 * @param changedPinCounts Counts of increments/decrements for each changed pin.
	 * @param falsePins A set of resources which were network-pinned to unpin them if they aren't in the canonical pin
	 * cache.
	 */
	void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins);

	/**
	 * Requests access to the explicit cache data.  Note that this is only exposed for write access since even reads of
	 * the cache cause it to change state, since it is least-recently-used.
	 * 
	 * @return The shared explicit cache data.
	 */
	ExplicitCacheData writableExplicitCache();

	/**
	 * Deletes the local accounting for the currently selected channel and instructs the IPFS node to delete the key.
	 * Even if this fails with an exception, it will still update the local data, just not delete the key from the node.
	 * 
	 * @throws IpfsConnectionException A network error prevented deletion of the key from the node (local data is still
	 * changed).
	 */
	void deleteChannelData() throws IpfsConnectionException;

	/**
	 * Allows direct read-write access to the shared favourites data projection instance.  This interface is provided
	 * for cache update and management logic.
	 * Calling this helper will mark the favoutites data as needing to be written-back, upon closing the access.
	 * 
	 * @return The shared favourites data instance.
	 */
	FavouritesCacheData writableFavouritesCache();
}
