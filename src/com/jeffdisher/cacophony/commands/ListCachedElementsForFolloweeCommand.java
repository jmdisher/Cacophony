package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.data.local.FollowingCacheElement;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		FollowIndex followIndex = local.loadFollowIndex();
		FollowRecord record = followIndex.getFollowerRecord(_followeeKey);
		if (null != record)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			Map<String, FollowingCacheElement> cachedMapByElementCid = CacheHelpers.createCachedMap(record);
			IpfsFile root = IpfsFile.fromIpfsCid(record.lastFetchedRoot());
			
			byte[] rawIndex = remote.readData(root);
			StreamIndex index = GlobalData.deserializeIndex(rawIndex);
			StreamRecords records = GlobalData.deserializeRecords(remote.readData(IpfsFile.fromIpfsCid(index.getRecords())));
			List<String> recordList = records.getRecord();
			executor.logToConsole("Followee has " + recordList.size() + " elements");
			for(String elementCid : recordList)
			{
				FollowingCacheElement element = cachedMapByElementCid.get(elementCid);
				String suffix = (null == element)
						? "(not cached)"
						: "(image: " + element.imageHash() + ", leaf: " + element.leafHash() + ")"
				;
				executor.logToConsole("Element CID: " + elementCid + " " + suffix);
			}
		}
		else
		{
			executor.fatalError(new Exception("Not following " + _followeeKey.key()));
		}
	}
}
