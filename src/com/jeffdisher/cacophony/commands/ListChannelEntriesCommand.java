package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.SingleThreadedScheduler;
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
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		INetworkScheduler scheduler = new SingleThreadedScheduler(remote);
		LoadChecker checker = new LoadChecker(scheduler, local.loadGlobalPinCache(), local.getSharedConnection());
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			// Make sure that they are a followee.
			FollowIndex followees = local.loadFollowIndex();
			FollowRecord record = followees.getFollowerRecord(_channelPublicKey);
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
		
		// Walk the elements, reading each element.
		for (String recordCid : records.getRecord())
		{
			StreamRecord record = (isCached
					? checker.loadCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
					: checker.loadNotCached(environment, IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
			).get();
			environment.logToConsole("element " + recordCid + ": " + record.getName());
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				environment.logToConsole("\t" + element.getCid() + " - " + element.getMime());
			}
		}
	}
}
