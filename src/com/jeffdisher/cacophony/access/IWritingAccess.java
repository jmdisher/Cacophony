package com.jeffdisher.cacophony.access;

import java.io.InputStream;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The aspects of the access design which require writing to the local storage (even if they seem network-only - some
 * network operations require updates to local storage state).
 */
public interface IWritingAccess extends IReadingAccess
{
	// TEMP
	HighLevelCache loadCacheReadWrite() throws IpfsConnectionException;

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
}
