package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		LoadChecker checker = new LoadChecker(remote, local);
		IpfsFile rootToLoad = null;
		if (null != _channelPublicKey)
		{
			// Make sure that they are a followee.
			FollowIndex followees = local.loadFollowIndex();
			FollowRecord record = followees.getFollowerRecord(_channelPublicKey);
			if (null == record)
			{
				throw new UsageException("Given public key (" + _channelPublicKey.toPublicKey() + ") is not being followed");
			}
			rootToLoad = record.lastFetchedRoot();
		}
		else
		{
			// This is us.
			LocalIndex localIndex = local.readIndex();
			rootToLoad = localIndex.lastPublishedIndex();
			Assert.assertTrue(null != rootToLoad);
		}
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(rootToLoad));
		byte[] rawRecords = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()));
		StreamRecords records = GlobalData.deserializeRecords(rawRecords);
		
		// Walk the elements, reading each element.
		for (String recordCid : records.getRecord())
		{
			byte[] rawRecord = checker.loadCached(IpfsFile.fromIpfsCid(recordCid));
			StreamRecord record = GlobalData.deserializeRecord(rawRecord);
			executor.logToConsole("element " + recordCid + ": " + record.getName());
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				executor.logToConsole("\t" + element.getCid() + " - " + element.getMime());
			}
		}
	}
}
