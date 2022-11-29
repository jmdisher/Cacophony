package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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
	 * @param access The local data store.
	 * @param name The name of the entry.
	 * @param description The description of the entry.
	 * @param discussionUrl The discussion URL of the entry (could be null).
	 * @param elements The list of elements we want to upload as attachments to this entry.
	 * @return The in-flight publish operation.
	 * @throws IpfsConnectionException Thrown if there is a network error talking to IPFS.
	 */
	public static FuturePublish uploadFileAndStartPublish(IEnvironment environment
			, IWritingAccess access
			, String name
			, String description
			, String discussionUrl
			, PublishElement[] elements
	) throws IpfsConnectionException, FailedDeserializationException
	{
		// Read the existing StreamIndex.
		IpfsKey publicKey = access.getPublicKey();
		
		IpfsFile previousRoot = access.getLastRootElement();
		Assert.assertTrue(null != previousRoot);
		StreamIndex index = access.loadCached(previousRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		IpfsFile previousRecords = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = access.loadCached(previousRecords, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		
		// Upload the elements - we will just do this one at a time, for simplicity (and since we are talking to a local node).
		DataArray array = new DataArray();
		for (PublishElement elt : elements)
		{
			IOperationLog eltLog = environment.logOperation("-Element: " + elt);
			// Upload the file - we won't close the file here since the caller needs to handle cases where something could go wrong (and they gave us the file so they should close it).
			IpfsFile uploaded = access.uploadAndPin(elt.fileData, false);
			
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
		IpfsFile recordHash = access.uploadAndPin(new ByteArrayInputStream(rawRecord), true);
		
		records.getRecord().add(recordHash.toSafeString());
		
		// Save the updated records and index.
		byte[] rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = access.uploadAndPin(new ByteArrayInputStream(rawRecords), true);
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncResult = access.uploadStoreAndPublishIndex(index);
		
		// Now that the new index has been uploaded and the publish is in progress, we can unpin the previous root and records.
		access.unpin(previousRoot);
		access.unpin(previousRecords);
		return asyncResult;
	}


	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}


	public static record PublishElement(String mime, InputStream fileData, int height, int width, boolean isSpecialImage) {}
}
