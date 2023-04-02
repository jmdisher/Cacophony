package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This command takes the CID of a specific StreamRecord and adds it to the user's record list as the latest post.
 * Since this effectively acts as though this post, typically from a different user, was posted by this user, the record
 * and all leaf elements it references will be pinned.
 */
public record RebroadcastCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException, FailedDeserializationException
	{
		if (null == _elementCid)
		{
			throw new UsageException("Element CID must be provided");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			// First, load our existing stream to make sure that this isn't a duplicate.
			IpfsFile previousRoot = access.getLastRootElement();
			Assert.assertTrue(null != previousRoot);
			StreamIndex index;
			IpfsFile previousRecords;
			StreamRecords records;
			try
			{
				index = access.loadCached(previousRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
				previousRecords = IpfsFile.fromIpfsCid(index.getRecords());
				records = access.loadCached(previousRecords, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
				String elementCidString = _elementCid.toSafeString();
				for (String elt : records.getRecord())
				{
					if (elementCidString.equals(elt))
					{
						throw new UsageException("Element is already posted to channel: " + _elementCid);
					}
				}
			}
			catch (IpfsConnectionException e)
			{
				// This is basically unexpected - it _can_ happen but it would be unusual for local data, given remote data worked, above.
				throw e;
			}
			catch (FailedDeserializationException e)
			{
				// This can't happen since we published this file.
				throw Assert.unexpected(e);
			}
			
			// Next, make sure that this actually _is_ a StreamRecord we can read.
			StreamRecord record = access.loadNotCached(_elementCid, (byte[] data) -> GlobalData.deserializeRecord(data)).get();
			// The record makes sense so pin it and everything it references (will throw on error).
			_pinReachableData(context.logger, access, record);
			newRoot = _updateStreamAndPublish(context.logger, access, previousRoot, index, previousRecords, records);
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

	private IpfsFile _updateStreamAndPublish(ILogger logger, IWritingAccess access, IpfsFile previousRoot, StreamIndex index, IpfsFile previousRecords, StreamRecords records) throws IpfsConnectionException, AssertionError
	{
		// If we get this far, that means that everything is pinned so this becomes a normal post operation.
		ILogger log = logger.logStart("Publishing to your stream...");
		IpfsFile newRoot;
		try
		{
			// Fetch and update the data.
			records.getRecord().add(_elementCid.toSafeString());
			
			// Serialize and write-back the updates.
			byte[] rawRecords = GlobalData.serializeRecords(records);
			IpfsFile recordsHash = access.uploadAndPin(new ByteArrayInputStream(rawRecords));
			index.setRecords(recordsHash.toSafeString());
			logger.logVerbose("Saving and publishing new index");
			newRoot = access.uploadIndexAndUpdateTracking(index);
			
			// Unpin the old meta-data.
			access.unpin(previousRoot);
			access.unpin(previousRecords);
			log.logFinish("Rebroadcast complete!");
		}
		catch (IpfsConnectionException e)
		{
			// This is basically unexpected - it _can_ happen but it would be unusual for local data, given remote data worked, above.
			throw e;
		}
		catch (SizeConstraintException e)
		{
			// This would require a change to the spec.
			throw Assert.unexpected(e);
		}
		return newRoot;
	}
}
