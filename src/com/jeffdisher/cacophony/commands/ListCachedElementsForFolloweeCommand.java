package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.List;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
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
		IpfsFile root = followIndex.getLastFetchedRoot(_followeeKey);
		if (null != root)
		{
			byte[] rawIndex = remote.readData(root);
			StreamIndex index = GlobalData.deserializeIndex(rawIndex);
			StreamRecords records = GlobalData.deserializeRecords(remote.readData(IpfsFile.fromIpfsCid(index.getRecords())));
			List<String> recordList = records.getRecord();
			System.out.println("Followee has " + recordList.size() + " elements");
			for(String elementCid : recordList)
			{
				System.out.println("Element CID: " + elementCid);
			}
		}
		else
		{
			executor.fatalError(new Exception("Not following " + _followeeKey.key()));
		}
	}
}
