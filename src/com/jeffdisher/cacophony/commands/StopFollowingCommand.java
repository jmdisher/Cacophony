package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.FolloweeRefreshLogic;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.StandardRefreshSupport;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
		IReadWriteLocalData localData = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = localData.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = localData.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler, connection);
		FollowIndex followIndex = localData.readFollowIndex();
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		
		// Removed the cache record and verify that we are following them.
		FollowRecord finalRecord = followIndex.checkoutRecord(_publicKey);
		if (null == finalRecord)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		// Prepare for the cleanup.
		GlobalPrefs prefs = localData.readGlobalPrefs();
		StandardRefreshSupport refreshSupport = new StandardRefreshSupport(environment, scheduler, cache);
		FollowingCacheElement[] updatedCacheState = FolloweeRefreshLogic.refreshFollowee(refreshSupport
				, prefs
				, finalRecord.elements()
				, finalRecord.lastFetchedRoot()
				, null
				, currentCacheUsageInBytes
		);
		// We were deleting everything, so this should be empty.
		Assert.assertTrue(0 == updatedCacheState.length);
		
		// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
		localData.writeFollowIndex(followIndex);
		localData.writeGlobalPinCache(pinCache);
		localData.writeLocalIndex(localIndex);
		localData.close();
		log.finish("Cleanup complete.  No longer following " + _publicKey);
	}
}
