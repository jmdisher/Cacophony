package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Cleaning up to stop following " + _publicKey + "...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// Removed the cache record and verify that we are following them.
		FollowRecord finalRecord = followIndex.removeFollowing(_publicKey);
		if (null == finalRecord)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		// Walk all the elements in the record stream, removing the cached meta-data and associated files.
		for (FollowingCacheElement element : finalRecord.elements())
		{
			if (null != element.imageHash())
			{
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.FILE, element.imageHash()).get();
			}
			if (null != element.leafHash())
			{
				cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.FILE, element.leafHash()).get();
			}
			cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, element.elementHash()).get();
		}
		
		// Remove all the root meta-data we have cached.
		IpfsFile lastRoot = finalRecord.lastFetchedRoot();
		StreamIndex streamIndex = checker.loadCached(lastRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		StreamDescription description = checker.loadCached(descriptionHash, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash).get();
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash).get();
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash).get();
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash).get();
		cache.removeFromFollowCache(_publicKey, HighLevelCache.Type.METADATA, lastRoot).get();
		// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
		local.writeBackConfig();
		log.finish("Cleanup complete.  No longer following " + _publicKey);
	}
}
