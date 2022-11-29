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
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshNextFolloweeCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException, SizeConstraintException, FailedDeserializationException
	{
		// We need to prune the cache before refreshing someone - hence, this needs to happen before we open storage.
		// We want to prune the cache to 90% for update so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, access, 0.90);
		
		FollowIndex followIndex = access.readWriteFollowIndex();
		
		IpfsKey publicKey = followIndex.nextKeyToPoll();
		if (null == publicKey)
		{
			throw new UsageException("Not following any users");
		}
		IOperationLog log = environment.logOperation("Refreshing followee " + publicKey + "...");
		
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		FollowRecord startRecord = followIndex.checkoutRecord(publicKey);
		Assert.assertTrue(null != startRecord);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = access.resolvePublicKey(publicKey).get();
		FollowRecord updatedRecord = null;
		if (null != indexRoot)
		{
			environment.logToConsole("Resolved as " + indexRoot);
			GlobalPrefs prefs = access.readGlobalPrefs();
			
			updatedRecord = CommandHelpers.doRefreshOfRecord(environment, access, currentCacheUsageInBytes, publicKey, startRecord, indexRoot, prefs);
		}
		else
		{
			// We couldn't resolve them so just advance the poll time.
			environment.logToConsole("Could not find followee");
			updatedRecord = new FollowRecord(publicKey, startRecord.lastFetchedRoot(), System.currentTimeMillis(), startRecord.elements());
		}
		followIndex.checkinRecord(updatedRecord);
		
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		log.finish("Follow successful!");
	}
}
