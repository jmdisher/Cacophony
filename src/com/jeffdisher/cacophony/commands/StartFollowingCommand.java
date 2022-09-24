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


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		LocalConfig local = environment.loadExistingConfig();
		
		// We need to prune the cache before adding someone and write this back before we proceed - hence, this needs to happen before we open storage.
		// We want to prune the cache to 75% for new followee so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, local, 0.75);
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
		IReadWriteLocalData localData = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = localData.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = localData.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		FollowIndex followIndex = localData.readFollowIndex();
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		
		// We need to first verify that we aren't already following them.
		if (null != followIndex.peekRecord(_publicKey))
		{
			throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
		}
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = scheduler.resolvePublicKey(_publicKey).get();
		Assert.assertTrue(null != indexRoot);
		environment.logToConsole("Resolved as " + indexRoot);
		GlobalPrefs prefs = localData.readGlobalPrefs();
		
		// This will throw exceptions in case something goes wrong.
		FollowRecord updatedRecord = CommandHelpers.doRefreshOfRecord(environment, scheduler, cache, checker, currentCacheUsageInBytes, _publicKey, null, indexRoot, prefs);
		followIndex.checkinRecord(updatedRecord);
		
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		localData.writeFollowIndex(followIndex);
		localData.writeGlobalPinCache(pinCache);
		localData.writeLocalIndex(localIndex);
		localData.close();
		log.finish("Follow successful!");
	}
}
