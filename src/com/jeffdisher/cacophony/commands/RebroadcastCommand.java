package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * This command takes the CID of a specific StreamRecord and adds it to the user's record list as the latest post.
 * Since this effectively acts as though this post, typically from a different user, was posted by this user, the record
 * and all leaf elements it references will be pinned.
 */
public record RebroadcastCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException, FailedDeserializationException, SizeConstraintException
	{
		if (null == _elementCid)
		{
			throw new UsageException("Element CID must be provided");
		}
		if (null == context.publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			HomeChannelModifier modifier = new HomeChannelModifier(access);
			
			// First, load our existing stream to make sure that this isn't a duplicate.
			StreamRecords records = modifier.loadRecords();
			String elementCidString = _elementCid.toSafeString();
			List<String> rawRecordCids = records.getRecord();
			if (rawRecordCids.contains(elementCidString))
			{
				throw new UsageException("Element is already posted to channel: " + _elementCid);
			}
			
			// Next, make sure that this actually _is_ a StreamRecord we can read.
			StreamRecord record = access.loadNotCached(_elementCid, "record", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeRecord(data)).get();
			
			// The record makes sense so pin it and everything it references (will throw on error).
			_pinReachableData(context.logger, access, record);
			
			// Add this element to the records and write it back.
			rawRecordCids.add(elementCidString);
			modifier.storeRecords(records);
			newRoot = modifier.commitNewRoot();
		}
		return new ChangedRoot(newRoot);
	}


	private void _pinReachableData(ILogger logger, IWritingAccess access, StreamRecord record) throws IpfsConnectionException
	{
		ILogger log = logger.logStart("Pinning StreamRecord " + _elementCid);
		List<FuturePin> pins = new ArrayList<>();
		pins.add(access.pin(_elementCid));
		for (DataElement elt : record.getElements().getElement())
		{
			IpfsFile file = IpfsFile.fromIpfsCid(elt.getCid());
			log.logOperation("Pinning leaf " + file);
			pins.add(access.pin(file));
		}
		IpfsConnectionException pinException = null;
		log.logVerbose("Waiting for pins...");
		for (FuturePin pin : pins)
		{
			try
			{
				pin.get();
			}
			catch (IpfsConnectionException e)
			{
				logger.logError("Failed to pin " + pin.cid + ": " + e.getLocalizedMessage());
				// We will just take any one of these exceptions and re-throw it.
				pinException = e;
			}
		}
		if (null == pinException)
		{
			log.logFinish("Pin success!");
		}
		else
		{
			log.logFinish("Pin failure!");
			// We failed to pin something, so we want to unpin everything, so we don't leak in this path (although this is still just best-efforts).
			for (FuturePin pin : pins)
			{
				access.unpin(pin.cid);
			}
			throw pinException;
		}
	}
}
