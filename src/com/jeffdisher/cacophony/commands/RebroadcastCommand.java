package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
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
public record RebroadcastCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _elementCid);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
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
			StreamRecord record = null;
			try
			{
				record = access.loadNotCached(_elementCid, (byte[] data) -> GlobalData.deserializeRecord(data)).get();
			}
			catch (IpfsConnectionException e)
			{
				environment.logError("Failed to load " + _elementCid + " from the network: " + e.getLocalizedMessage());
			}
			catch (FailedDeserializationException e)
			{
				environment.logError("Post " + _elementCid + " exists but is NOT readable as a StreamRecord: " + e.getLocalizedMessage());
			}
			if (null != record)
			{
				// The record makes sense so pin it and everything it references.
				boolean didPinAll = _pinReachableData(environment, access, record);
				
				if (didPinAll)
				{
					_updateStreamAndPublish(environment, access, previousRoot, index, previousRecords, records);
				}
				else
				{
					environment.logError("Failed to pin the record elements so rebroadcast aborted!");
				}
			}
		}
	}


	private boolean _pinReachableData(IEnvironment environment, IWritingAccess access, StreamRecord record) throws IpfsConnectionException
	{
		environment.logToConsole("Pinning StreamRecord " + _elementCid);
		List<FuturePin> pins = new ArrayList<>();
		pins.add(access.pin(_elementCid));
		for (DataElement elt : record.getElements().getElement())
		{
			IpfsFile file = IpfsFile.fromIpfsCid(elt.getCid());
			environment.logToConsole("Pinning leaf " + file);
			pins.add(access.pin(file));
		}
		boolean isSuccess = true;
		environment.logToConsole("Waiting for pins...");
		for (FuturePin pin : pins)
		{
			try
			{
				pin.get();
			}
			catch (IpfsConnectionException e)
			{
				environment.logError("Failed to pin " + pin.cid + ": " + e.getLocalizedMessage());
				isSuccess = false;
			}
		}
		if (!isSuccess)
		{
			// We failed to pin something, so we want to unpin everything, so we don't leak in this path (although this is still just best-efforts).
			for (FuturePin pin : pins)
			{
				access.unpin(pin.cid);
			}
		}
		return isSuccess;
	}

	private void _updateStreamAndPublish(IEnvironment environment, IWritingAccess access, IpfsFile previousRoot, StreamIndex index, IpfsFile previousRecords, StreamRecords records) throws IpfsConnectionException, AssertionError
	{
		// If we get this far, that means that everything is pinned so this becomes a normal post operation.
		environment.logToConsole("Publishing to your stream...");
		try
		{
			// Fetch and update the data.
			records.getRecord().add(_elementCid.toSafeString());
			
			// Serialize and write-back the updates.
			byte[] rawRecords = GlobalData.serializeRecords(records);
			IpfsFile recordsHash = access.uploadAndPin(new ByteArrayInputStream(rawRecords));
			index.setRecords(recordsHash.toSafeString());
			environment.logToConsole("Saving and publishing new index");
			IpfsFile newRoot = access.uploadIndexAndUpdateTracking(index);
			
			// Unpin the old meta-data.
			access.unpin(previousRoot);
			access.unpin(previousRecords);
			
			// Publish new root.
			FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
			CommandHelpers.commonWaitForPublish(environment, asyncPublish);
			environment.logToConsole("Rebroadcast complete!");
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
	}
}
