package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.data.local.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.logic.Executor.IOperationLog;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		LocalIndex localIndex = ValidationHelpers.requireIndex(local);
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local.loadGlobalPinCache(), local.getSharedConnection());
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// Removed the cache record and verify that we are following them.
		FollowRecord finalRecord = followIndex.removeFollowing(_publicKey);
		if (null == finalRecord)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		IOperationLog log = executor.logOperation("Cleaning up to stop following " + _publicKey + "...");
		// Walk all the elements in the record stream, removing the cached meta-data and associated files.
		for (FollowingCacheElement element : finalRecord.elements())
		{
			if (null != element.imageHash())
			{
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.FILE, element.imageHash());
			}
			if (null != element.leafHash())
			{
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.FILE, element.leafHash());
			}
			cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, element.elementHash());
		}
		
		// Remove all the root meta-data we have cached.
		IpfsFile lastRoot = finalRecord.lastFetchedRoot();
		StreamIndex streamIndex = GlobalData.deserializeIndex(checker.loadCached(lastRoot));
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		StreamDescription description = GlobalData.deserializeDescription(checker.loadCached(descriptionHash));
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash);
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, lastRoot);
		log.finish("Cleanup complete.  No longer following " + _publicKey);
	}
}
