package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// Verify that we are following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(_publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Remove all the cached records.
		// TODO:  Implement.
		
		// Remove all the root meta-data we have cached.
		StreamIndex streamIndex = GlobalData.deserializeIndex(remote.readData(lastRoot));
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		StreamDescription description = GlobalData.deserializeDescription(remote.readData(descriptionHash));
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, lastRoot);
		
		followIndex.removeFollowing(_publicKey);
	}
}
