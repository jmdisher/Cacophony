package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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
	 * @return The result of the publish.
	 * @throws IpfsConnectionException Thrown if there is a network error talking to IPFS.
	 * @throws SizeConstraintException The meta-data tree serialized to be too large to store.
	 */
	public static PublishResult uploadFileAndUpdateTracking(IEnvironment.IOperationLog callerLog
			, IWritingAccess access
			, String name
			, String description
			, String discussionUrl
			, PublishElement[] elements
	) throws IpfsConnectionException, SizeConstraintException
	{
		// Read the existing StreamIndex.
		IpfsKey publicKey = access.getPublicKey();
		
		IpfsFile previousRoot = access.getLastRootElement();
		Assert.assertTrue(null != previousRoot);
		StreamIndex index = _safeRead(access.loadCached(previousRoot, (byte[] data) -> GlobalData.deserializeIndex(data)));
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		IpfsFile previousRecords = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = _safeRead(access.loadCached(previousRecords, (byte[] data) -> GlobalData.deserializeRecords(data)));
		
		// Upload the elements - we will just do this one at a time, for simplicity (and since we are talking to a local node).
		DataArray array = new DataArray();
		for (PublishElement elt : elements)
		{
			IOperationLog eltLog = callerLog.logStart("-Element: " + elt);
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
		IpfsFile recordHash = access.uploadAndPin(new ByteArrayInputStream(rawRecord));
		
		List<String> recordCids = records.getRecord();
		String newRecordCid = recordHash.toSafeString();
		// This assertion is just to avoid some corner-cases which can happen in testing but have no obvious meaning in real usage.
		// This can happen when 2 posts are made before the time has advanced.
		// While the record list is allowed to contain duplicates, this is usually just the result of a test running too quickly or being otherwise incorrect.
		// (We could make this into an actual error case if it were meaningful).
		Assert.assertTrue(!recordCids.contains(newRecordCid));
		recordCids.add(newRecordCid);
		
		// Save the updated records and index.
		byte[] rawRecords = GlobalData.serializeRecords(records);
		IpfsFile recordsHash = access.uploadAndPin(new ByteArrayInputStream(rawRecords));
		
		// Update, save, and publish the new index.
		index.setRecords(recordsHash.toSafeString());
		callerLog.logVerbose("Saving and publishing new index");
		IpfsFile newRoot = access.uploadIndexAndUpdateTracking(index);
		
		// Now that the new index has been uploaded, we can unpin the previous root and records.
		// (we may want more explicit control over this, in the future)
		access.unpin(previousRoot);
		access.unpin(previousRecords);
		return new PublishResult(newRoot, recordHash);
	}


	private static long _currentUtcEpochSeconds()
	{
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return now.toEpochSecond();
	}

	private static <R> R _safeRead(FutureRead<R> future) throws IpfsConnectionException
	{
		try
		{
			return future.get();
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			// We call this for local user data so we can't fail to deserialize.
			throw Assert.unexpected(e);
		}
	}


	public static record PublishElement(String mime, InputStream fileData, int height, int width, boolean isSpecialImage) {}

	public static record PublishResult(IpfsFile newIndexRoot, IpfsFile newRecordCid) {}
}
