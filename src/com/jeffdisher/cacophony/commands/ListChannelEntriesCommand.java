package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		
		// Read the data elements.
		LocalIndex localIndex = null;
		FollowIndex followIndex = null;
		GlobalPinCache pinCache = null;
		try (IReadOnlyLocalData localData = local.getSharedLocalData().openForRead())
		{
			localIndex = localData.readLocalIndex();
			followIndex = localData.readFollowIndex();
			pinCache = localData.readGlobalPinCache();
		}
		INetworkScheduler scheduler = environment.getSharedScheduler(local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(scheduler, pinCache, local.getSharedConnection());
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			// Make sure that they are a followee.
			FollowRecord record = followIndex.getFollowerRecord(_channelPublicKey);
			if (null != record)
			{
				environment.logToConsole("Following " + _channelPublicKey);
				rootToLoad = record.lastFetchedRoot();
				isCached = true;
			}
			else
			{
				environment.logToConsole("NOT following " + _channelPublicKey);
				rootToLoad = scheduler.resolvePublicKey(_channelPublicKey).get();
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _channelPublicKey);
				}
				isCached = false;
			}
		}
		else
		{
			// This is us.
			rootToLoad = localIndex.lastPublishedIndex();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		StreamIndex index = checker.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		StreamRecords records = (isCached
				? checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
				: checker.loadNotCached(environment, IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
		).get();
		
		// Start the async StreamRecord loads.
		List<AsyncRecord> asyncRecords = new ArrayList<>();
		for (String recordCid : records.getRecord())
		{
			FutureRead<StreamRecord> future = (isCached
					? checker.loadCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
					: checker.loadNotCached(environment, IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
			);
			asyncRecords.add(new AsyncRecord(recordCid, future));
		}
		
		// Walk the elements, reading each element.
		for (AsyncRecord asyncRecord : asyncRecords)
		{
			StreamRecord record = asyncRecord.future.get();
			environment.logToConsole("element " + asyncRecord.recordCid + ": " + record.getName());
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				environment.logToConsole("\t" + element.getCid() + " - " + element.getMime());
			}
		}
	}


	private static record AsyncRecord(String recordCid, FutureRead<StreamRecord> future)
	{
	}
}
