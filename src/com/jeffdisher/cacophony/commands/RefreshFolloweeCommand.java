package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _publicKey);
		
		LocalConfig local = environment.loadExistingConfig();
		
		// We need to prune the cache before refreshing someone - hence, this needs to happen before we open storage.
		// We want to prune the cache to 90% for update so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, local, 0.90);
		
		IOperationLog log = environment.logOperation("Refreshing followee " + _publicKey + "...");
		IReadWriteLocalData localData = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = localData.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = localData.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler, connection);
		FollowIndex followIndex = localData.readFollowIndex();
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		
		// We need to first verify that we are already following them.
		FollowRecord startRecord = followIndex.checkoutRecord(_publicKey);
		if (null == startRecord)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = scheduler.resolvePublicKey(_publicKey).get();
		FollowRecord updatedRecord = null;
		if (null != indexRoot)
		{
			environment.logToConsole("Resolved as " + indexRoot);
			GlobalPrefs prefs = localData.readGlobalPrefs();
			
			updatedRecord = CommandHelpers.doRefreshOfRecord(environment, scheduler, cache, currentCacheUsageInBytes, _publicKey, startRecord, indexRoot, prefs);
		}
		else
		{
			// We couldn't resolve them so just advance the poll time.
			environment.logToConsole("Could not find followee");
			updatedRecord = new FollowRecord(_publicKey, startRecord.lastFetchedRoot(), System.currentTimeMillis(), startRecord.elements());
		}
		followIndex.checkinRecord(updatedRecord);
		
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		localData.writeFollowIndex(followIndex);
		localData.writeGlobalPinCache(pinCache);
		localData.writeLocalIndex(localIndex);
		localData.close();
		log.finish("Follow successful!");
	}
}
