package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IReadingAccess access) throws IpfsConnectionException, UsageException, KeyException
	{
		INetworkScheduler scheduler = access.scheduler();
		FollowIndex followIndex = access.readOnlyFollowIndex();
		
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			// Make sure that they are a followee.
			FollowRecord record = followIndex.peekRecord(_channelPublicKey);
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
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		StreamIndex index = access.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		StreamRecords records = (isCached
				? access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
				: access.loadNotCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
		).get();
		
		// Start the async StreamRecord loads.
		List<AsyncRecord> asyncRecords = new ArrayList<>();
		for (String recordCid : records.getRecord())
		{
			FutureRead<StreamRecord> future = (isCached
					? access.loadCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
					: access.loadNotCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
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
