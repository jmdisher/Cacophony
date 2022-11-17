package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Basic helpers used by the PublishCommand, split out so that we can call these without needing to invoke the command,
 * as an opaque unit, from the interactive server.
 */
public class PublishHelpers
{
	/**
	 * Upload the given files, create the record entry for the new post, append it to the record stream, and begin the
	 * publish operation.
	 * 
	 * @param environment Used for logging.
	 * @param scheduler Used for scheduling upload operations.
	 * @param connection The IPFS connection.
	 * @param localData The local data store.
	 * @param existingLocalIndex The state of the index file before any updates to account for this.
	 * @param pinCache The cache of what data is pinned on the local node.
	 * @param cache The high-level cache tracking the data we are uploading.
	 * @param name The name of the entry.
	 * @param description The description of the entry.
	 * @param discussionUrl The discussion URL of the entry (could be null).
	 * @param elements The list of elements we want to upload as attachments to this entry.
	 * @return The in-flight publish operation.
	 * @throws IpfsConnectionException Thrown if there is a network error talking to IPFS.
	 */
	public static FuturePublish uploadFileAndStartPublish(IEnvironment environment
			, INetworkScheduler scheduler
			, IConnection connection
			, IReadWriteLocalData localData
			, LocalIndex existingLocalIndex
			, GlobalPinCache pinCache
			, HighLevelCache cache
			, String name
			, String description
			, String discussionUrl
			, PublishElement[] elements
	) throws IpfsConnectionException
	{
		// Read the existing StreamIndex.
		IpfsKey publicKey = scheduler.getPublicKey();
		
		IpfsFile previousRoot = existingLocalIndex.lastPublishedIndex();
		Assert.assertTrue(null != previousRoot);
		StreamIndex index = cache.loadCached(previousRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		StreamRecords records = cache.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		
		// Upload the elements.
		List<FutureSave> futureSaves = new ArrayList<>();
		for (PublishElement elt : elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Upload the file - we won't close the file here since the caller needs to handle cases where something could go wrong (and they gave us the file so they should close it).
			FutureSave save = scheduler.saveStream(elt.fileData, false);
			futureSaves.add(save);
			eltLog.finish("-in progress...");
		}
		
		DataArray array = new DataArray();
		Iterator<FutureSave> futureIterator = futureSaves.iterator();
		for (PublishElement elt : elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Wait for file upload.
			FutureSave save = futureIterator.next();
			IpfsFile uploaded = save.get();
			cache.uploadedToThisCache(uploaded);
			
			DataElement element = new DataElement();
			element.setCid(uploaded.toSafeString());
			element.setMime(elt.mime());
			element.setHeight(elt.height());
			element.setWidth(elt.width());
			if (elt.isSpecialImage())
			{
				element.setSpecial(ElementSpecialType.IMAGE);
			}
			array.getElement().add(element);
			eltLog.finish("-Done!");
		}
		Assert.assertTrue(!futureIterator.hasNext());
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription(description);
		if (null != discussionUrl)
		{
			record.setDiscussion(discussionUrl);
		}
		record.setElements(array);
		record.setPublisherKey(publicKey.toPublicKey());
		// The published time is in seconds since the Epoch, in UTC.
		record.setPublishedSecondsUtc(_currentUtcEpochSeconds());
		byte[] rawRecord = GlobalData.serializeRecord(record);
		IpfsFile recordHash = scheduler.saveStream(new ByteArrayInputStream(rawRecord), true).get();
		cache.uploadedToThisCache(recordHash);
		
		records.getRecord().add(recordHash.toSafeString());
		
		// Save the updated records and index.
		byte[] rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = scheduler.saveStream(new ByteArrayInputStream(rawRecords), true).get();
		cache.uploadedToThisCache(recordsHash);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncResult = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		
		// Do the local storage update while the publish continues in the background (even if it fails, we still want to update local storage).
		CommandHelpers.commonUpdateIndex(environment, localData, existingLocalIndex, cache, previousRoot, asyncResult.getIndexHash());
		return asyncResult;
	}


	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}


	public static record PublishElement(String mime, InputStream fileData, int height, int width, boolean isSpecialImage) {}
}
