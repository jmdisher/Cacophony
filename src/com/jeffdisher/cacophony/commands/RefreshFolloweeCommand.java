package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _publicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, SizeConstraintException, UsageException
	{
		// We need to prune the cache before refreshing someone - hence, this needs to happen before we open storage.
		// We want to prune the cache to 90% for update so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, access, 0.90);
		
		INetworkScheduler scheduler = access.scheduler();
		FollowIndex followIndex = access.readWriteFollowIndex();
		
		IOperationLog log = environment.logOperation("Refreshing followee " + _publicKey + "...");
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
			GlobalPrefs prefs = access.readGlobalPrefs();
			
			updatedRecord = CommandHelpers.doRefreshOfRecord(environment, scheduler, access, currentCacheUsageInBytes, _publicKey, startRecord, indexRoot, prefs);
		}
		else
		{
			// We couldn't resolve them so just advance the poll time.
			environment.logToConsole("Could not find followee");
			updatedRecord = new FollowRecord(_publicKey, startRecord.lastFetchedRoot(), System.currentTimeMillis(), startRecord.elements());
		}
		followIndex.checkinRecord(updatedRecord);
		
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		log.finish("Follow successful!");
	}
}
