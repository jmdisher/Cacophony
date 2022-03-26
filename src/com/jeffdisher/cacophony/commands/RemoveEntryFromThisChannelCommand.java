package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.logic.Executor.IOperationLog;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		IOperationLog log = executor.logOperation("Removing entry " + _elementCid + " from channel...");
		LocalIndex localIndex = ValidationHelpers.requireIndex(local);
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		
		// The general idea here is that we want to unpin all data elements associated with this, but only after we update the record stream and channel index (since broken data will cause issues for followers).
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(rootToLoad));
		
		// Read the existing stream so we can append to it (we do this first just to verify integrity is fine).
		byte[] rawRecords = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
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
		
		if (didFind)
		{
			// Update the record list and stream index.
			records.getRecord().remove(foundIndex);
			rawRecords = GlobalData.serializeRecords(records);
			IpfsFile newCid = remote.saveData(rawRecords);
			cache.uploadedToThisCache(newCid);
			index.setRecords(newCid.toSafeString());
			executor.logToConsole("Saving and publishing new index");
			IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(executor, remote, local, index);
			cache.uploadedToThisCache(indexHash);
			
			// Finally, unpin the entries (we need to unpin them all since we own them so we added them all).
			byte[] rawRecord = checker.loadCached(_elementCid);
			StreamRecord record = GlobalData.deserializeRecord(rawRecord);
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				IpfsFile cid = IpfsFile.fromIpfsCid(element.getCid());
				cache.removeFromThisCache(cid);
			}
			cache.removeFromThisCache(_elementCid);
		}
		else
		{
			executor.fatalError(new Exception("CID " + _elementCid + " not found in record list"));
		}
		
		// Remove the old root.
		cache.removeFromThisCache(rootToLoad);
		log.finish("Entry removed: " + _elementCid);
	}
}
