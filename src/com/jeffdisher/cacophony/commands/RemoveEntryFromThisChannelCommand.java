package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		Assert.assertTrue(null != _elementCid);
		
		IOperationLog log = environment.logOperation("Removing entry " + _elementCid + " from channel...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local.loadGlobalPinCache(), local.getSharedConnection());
		HighLevelCache cache = new HighLevelCache(local.loadGlobalPinCache(), local.getSharedConnection());
		
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
			environment.logToConsole("Saving and publishing new index");
			IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(remote, local, index);
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
			throw new UsageException("CID " + _elementCid + " not found in record list");
		}
		
		// Remove the old root.
		cache.removeFromThisCache(rootToLoad);
		local.writeBackConfig();
		log.finish("Entry removed: " + _elementCid);
	}
}
