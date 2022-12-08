package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, SizeConstraintException, UsageException, FailedDeserializationException
	{
		// We need to prune the cache before refreshing someone - hence, this needs to happen before we open storage.
		// We want to prune the cache to 90% for update so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, access, 0.90);
		
		IFolloweeWriting followees = access.writableFolloweeData();
		
		IOperationLog log = environment.logOperation("Refreshing followee " + _publicKey + "...");
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followees);
		
		// We need to first verify that we are already following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = access.resolvePublicKey(_publicKey).get();
		IpfsFile newRoot = null;
		if (null != indexRoot)
		{
			environment.logToConsole("Resolved as " + indexRoot);
			GlobalPrefs prefs = access.readGlobalPrefs();
			
			boolean didRefresh = CommandHelpers.doRefreshOfRecord(environment, access, followees, _publicKey, currentCacheUsageInBytes, lastRoot, indexRoot, prefs);
			newRoot = didRefresh
					? indexRoot
					: lastRoot
			;
		}
		else
		{
			// We couldn't resolve them so just advance the poll time.
			environment.logToConsole("Could not find followee");
			newRoot = lastRoot;
		}
		long lastPollMillis = System.currentTimeMillis();
		followees.updateExistingFollowee(_publicKey, newRoot, lastPollMillis);
		
		log.finish("Follow successful!");
	}
}
