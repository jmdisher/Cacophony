package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListRecommendationsCommand(IpfsKey _publicKey) implements ICommand
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
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler, local.getSharedConnection());
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _publicKey)
		{
			publicKey = _publicKey;
			FollowRecord existingRecord = followIndex.peekRecord(_publicKey);
			if (null != existingRecord)
			{
				environment.logToConsole("Following " + _publicKey);
				rootToLoad = existingRecord.lastFetchedRoot();
				isCached = true;
			}
			else
			{
				environment.logToConsole("NOT following " + _publicKey);
				rootToLoad = scheduler.resolvePublicKey(_publicKey).get();
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _publicKey);
				}
			}
		}
		else
		{
			// Just list our recommendations.
			// Read the existing StreamIndex.
			publicKey = scheduler.getPublicKey();
			rootToLoad = localIndex.lastPublishedIndex();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		StreamIndex index = (isCached
				? cache.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
				: cache.loadNotCached(environment, rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
		).get();
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = (isCached
				? cache.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data))
				: cache.loadNotCached(environment, IpfsFile.fromIpfsCid(index.getRecommendations()), (byte[] data) -> GlobalData.deserializeRecommendations(data))
		).get();
		
		// Walk the recommendations and print their keys to the console.
		environment.logToConsole("Recommendations of " + publicKey.toPublicKey());
		for (String rawKey : recommendations.getUser())
		{
			environment.logToConsole("\t" + rawKey);
		}
	}
}
