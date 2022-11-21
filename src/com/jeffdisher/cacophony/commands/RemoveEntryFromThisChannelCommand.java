package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _elementCid);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IOperationLog log = environment.logOperation("Removing entry " + _elementCid + " from channel...");
			CleanupData cleanup = _runCore(environment, access);
			
			// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
			_runFinish(environment, access, cleanup);
			log.finish("Entry removed: " + _elementCid);
		}
	}


	private CleanupData _runCore(IEnvironment environment, IWritingAccess access) throws UsageException, IpfsConnectionException
	{
		LocalIndex localIndex = access.readOnlyLocalIndex();
		INetworkScheduler scheduler = access.scheduler();
		HighLevelCache cache = access.loadCacheReadWrite();
		
		// The general idea here is that we want to unpin all data elements associated with this, but only after we update the record stream and channel index (since broken data will cause issues for followers).
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = access.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		IpfsFile previousRecords = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = access.loadCached(previousRecords, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		
		// Make sure that we actually have the record.
		boolean didFind = false;
		String search = _elementCid.toSafeString();
		int foundIndex = 0;
		for (String cid : records.getRecord())
		{
			if (search.equals(cid))
			{
				didFind = true;
				break;
			}
			foundIndex += 1;
		}
		
		if (!didFind)
		{
			throw new UsageException("CID " + _elementCid + " not found in record list");
		}
		
		// Update the record list and stream index.
		records.getRecord().remove(foundIndex);
		byte[] rawRecords = GlobalData.serializeRecords(records);
		IpfsFile newCid = scheduler.saveStream(new ByteArrayInputStream(rawRecords), true).get();
		cache.uploadedToThisCache(newCid);
		index.setRecords(newCid.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncPublish = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		return new CleanupData(asyncPublish, rootToLoad, previousRecords);
	}

	private void _runFinish(IEnvironment environment, IWritingAccess access, CleanupData data) throws IpfsConnectionException
	{
		LocalIndex localIndex = access.readOnlyLocalIndex();
		HighLevelCache cache = access.loadCacheReadWrite();
		
		// Unpin the entries (we need to unpin them all since we own them so we added them all).
		StreamRecord record = null;
		try
		{
			record = access.loadCached(_elementCid, (byte[] raw) -> GlobalData.deserializeRecord(raw)).get();
		}
		catch (IpfsConnectionException e)
		{
			environment.logError("WARNING: Failed to load element being removed: " +  _elementCid + ".  Any referenced elements will need to be manually unpinned.");
		}
		if (null != record)
		{
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				IpfsFile cid = IpfsFile.fromIpfsCid(element.getCid());
				CommandHelpers.safeRemoveFromLocalNode(environment, cache, cid);
			}
			CommandHelpers.safeRemoveFromLocalNode(environment, cache, _elementCid);
		}
		
		// Now that the new index has been uploaded and the publish is in progress, we can unpin the previous root and records.
		CommandHelpers.commonUpdateIndex(environment, access, localIndex, cache, data.oldRootHash, data.asyncPublish.getIndexHash());
		CommandHelpers.safeRemoveFromLocalNode(environment, cache, data.previousRecords);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, data.asyncPublish);
	}


	private static record CleanupData(FuturePublish asyncPublish, IpfsFile oldRootHash, IpfsFile previousRecords) {}
}
