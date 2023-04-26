package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Basic helpers used by the PublishCommand, split out so that we can call these without needing to invoke the command,
 * as an opaque unit, from the interactive server.
 */
public class PublishHelpers
{
	/**
	 * Upload the given files, create the record entry for the new post, append it to the record stream.
	 * 
	 * @param callerLog Used for logging.
	 * @param access The local data store.
	 * @param name The name of the entry.
	 * @param description The description of the entry.
	 * @param discussionUrl The discussion URL of the entry (could be null).
	 * @param elements The list of elements we want to upload as attachments to this entry.
	 * @param publisherKey The back-reference to the publisher's public key.
	 * @return The result of the publish.
	 * @throws IpfsConnectionException Thrown if there is a network error talking to IPFS.
	 * @throws SizeConstraintException The meta-data tree serialized to be too large to store.
	 */
	public static PublishResult uploadFileAndUpdateTracking(ILogger callerLog
			, IWritingAccess access
			, String name
			, String description
			, String discussionUrl
			, PublishElement[] elements
			, IpfsKey publisherKey
	) throws IpfsConnectionException, SizeConstraintException
	{
		// Upload the elements - we will just do this one at a time, for simplicity (and since we are talking to a local node).
		DataArray array = new DataArray();
		for (PublishElement elt : elements)
		{
			ILogger eltLog = callerLog.logStart("-Element: " + elt);
			// Note that this call will close the file (since it is intended to drain it) but we will still close it in
			// the caller, just to cover error cases before getting this far.
			IpfsFile uploaded = access.uploadAndPin(elt.fileData);
			
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
			eltLog.logFinish("-Done!");
		}
		
		// Assemble and upload the new StreamRecord.
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription(description);
		if (null != discussionUrl)
		{
			record.setDiscussion(discussionUrl);
		}
		record.setElements(array);
		record.setPublisherKey(publisherKey.toPublicKey());
		// The published time is in seconds since the Epoch, in UTC.
		record.setPublishedSecondsUtc(_currentUtcEpochSeconds());
		byte[] rawRecord = GlobalData.serializeRecord(record);
		IpfsFile recordHash = access.uploadAndPin(new ByteArrayInputStream(rawRecord));
		
		// Now, update the channel data structure.
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		StreamRecords records = modifier.loadRecords();
		List<String> recordCids = records.getRecord();
		String newRecordCid = recordHash.toSafeString();
		// This assertion is just to avoid some corner-cases which can happen in testing but have no obvious meaning in real usage.
		// This can happen when 2 posts are made before the time has advanced.
		// While the record list is allowed to contain duplicates, this is usually just the result of a test running too quickly or being otherwise incorrect.
		// (We could make this into an actual error case if it were meaningful).
		Assert.assertTrue(!recordCids.contains(newRecordCid));
		recordCids.add(newRecordCid);
		
		// Save the updated records and index.
		modifier.storeRecords(records);
		
		callerLog.logVerbose("Saving and publishing new index");
		IpfsFile newRoot = modifier.commitNewRoot();
		return new PublishResult(newRoot, recordHash);
	}


	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}


	public static record PublishElement(String mime, InputStream fileData, int height, int width, boolean isSpecialImage) {}

	public static record PublishResult(IpfsFile newIndexRoot, IpfsFile newRecordCid) {}
}
