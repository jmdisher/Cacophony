package com.jeffdisher.cacophony.access;

import java.io.InputStream;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
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
	// TEMP.
	FollowIndex readWriteFollowIndex();

	/**
	 * Writes back the given prefs to disk.
	 * 
	 * @param prefs The new prefs object.
	 */
	void writeGlobalPrefs(GlobalPrefs prefs);

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
	 * This method wraps up several operations which make up the root index update into a single call:
	 * -serializes the given streamIndex
	 * -uploads it to the local node
	 * -adds it to the local pin cache
	 * -updates local storage to know this new index as the root element for the channel
	 * -initiates the asynchronous publish operation
	 * 
	 * @param streamIndex The new stream index.
	 * @return The asynchronous publish.
	 * @throws IpfsConnectionException If there was a problem contacting the IPFS node.
	 */
	FuturePublish uploadStoreAndPublishIndex(StreamIndex streamIndex) throws IpfsConnectionException;

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
}
