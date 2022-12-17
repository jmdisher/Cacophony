package com.jeffdisher.cacophony.access;

import java.io.InputStream;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
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
	 * Uploads the data in dataToSave, recording that the file is pinned locally.
	 * 
	 * @param dataToSave The data stream to write to the server.
	 * @param shouldCloseStream True if the helper should internally close this stream when done the upload.
	 * @return The hash of the saved file.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	IpfsFile uploadAndPin(InputStream dataToSave, boolean shouldCloseStream) throws IpfsConnectionException;

	/**
	 * Uploads the new StreamIndex and updates local tracking.
	 * Note that this doesn't republish the new index, as that needs to be explicitly done.
	 * 
	 * @param streamIndex The new stream index.
	 * @return The CID of the published index.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	IpfsFile uploadIndexAndUpdateTracking(StreamIndex streamIndex) throws IpfsConnectionException;

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
	 * Requests that the given indexRoot be published under our key, to the IPFS network.
	 * 
	 * @param indexRoot The root to publish.
	 * @return The asynchronous publish operation.
	 */
	FuturePublish beginIndexPublish(IpfsFile indexRoot);
}
