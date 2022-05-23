package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
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
		
		IOperationLog log = environment.logOperation("Removing entry " + _elementCid + " from channel...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		HighLevelCache cache = new HighLevelCache(pinCache, connection);
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, pinCache, connection);
		CleanupData cleanup = _runCore(environment, connection, localIndex, pinCache, cache, remote, checker);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		_runFinish(environment, local, localIndex, cache, checker, cleanup);
		log.finish("Entry removed: " + _elementCid);
	}


	private CleanupData _runCore(IEnvironment environment, IConnection connection, LocalIndex localIndex, GlobalPinCache pinCache, HighLevelCache cache, RemoteActions remote, LoadChecker checker) throws UsageException, IpfsConnectionException
	{
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
		
		if (!didFind)
		{
			throw new UsageException("CID " + _elementCid + " not found in record list");
		}
		
		// Update the record list and stream index.
		records.getRecord().remove(foundIndex);
		rawRecords = GlobalData.serializeRecords(records);
		IpfsFile newCid = remote.saveData(rawRecords);
		cache.uploadedToThisCache(newCid);
		index.setRecords(newCid.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(remote, index);
		return new CleanupData(indexHash, rootToLoad);
	}

	private void _runFinish(IEnvironment environment, LocalConfig local, LocalIndex localIndex, HighLevelCache cache, LoadChecker checker, CleanupData data)
	{
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), data.indexHash));
		cache.uploadedToThisCache(data.indexHash);
		
		// Finally, unpin the entries (we need to unpin them all since we own them so we added them all).
		byte[] rawRecord = null;
		try
		{
			rawRecord = checker.loadCached(_elementCid);
		}
		catch (IpfsConnectionException e)
		{
			System.err.println("WARNING: Failed to load element being removed: " +  _elementCid + ".  Any referenced elements will need to be manually unpinned.");
		}
		if (null != rawRecord)
		{
			StreamRecord record = GlobalData.deserializeRecord(rawRecord);
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				IpfsFile cid = IpfsFile.fromIpfsCid(element.getCid());
				_safeRemove(cache, cid);
			}
			_safeRemove(cache, _elementCid);
		}
		
		// Remove the old root.
		_safeRemove(cache, data.oldRootHash);
		local.writeBackConfig();
	}

	private static void _safeRemove(HighLevelCache cache, IpfsFile file)
	{
		try
		{
			cache.removeFromThisCache(file);
		}
		catch (IpfsConnectionException e)
		{
			System.err.println("WARNING: Error unpinning " + file + ".  This will need to be done manually.");
		}
	}


	private static record CleanupData(IpfsFile indexHash, IpfsFile oldRootHash) {}
}
