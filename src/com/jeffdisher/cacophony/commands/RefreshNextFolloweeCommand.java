package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
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


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeWriting followees = access.writableFolloweeData();
		
		IpfsKey publicKey = followees.getNextFolloweeToPoll();
		if (null == publicKey)
		{
			throw new UsageException("Not following any users");
		}
		IOperationLog log = environment.logOperation("Refreshing followee " + publicKey + "...");
		
		// We need to prune the cache before refreshing someone - hence, this needs to happen before we open storage.
		// We want to prune the cache to 90% for update so make space.
		CommandHelpers.shrinkCacheToFitInPrefs(environment, access, 0.90);
		
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(publicKey);
		Assert.assertTrue(null != lastRoot);
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = access.resolvePublicKey(publicKey).get();
		IpfsFile newRoot = null;
		boolean isSuccess = false;
		if (null != indexRoot)
		{
			environment.logToConsole("Resolved as " + indexRoot);
			PrefsData prefs = access.readPrefs();
			
			boolean didRefresh = false;
			try
			{
				long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followees);
				didRefresh = CommandHelpers.doRefreshOfRecord(environment, access, followees, publicKey, currentCacheUsageInBytes, lastRoot, indexRoot, prefs);
			}
			catch (IpfsConnectionException e)
			{
				throw e;
			}
			catch (SizeConstraintException e)
			{
				environment.logToConsole("Meta-data element too big (probably wrong file published): " + e.getLocalizedMessage());
				environment.logToConsole("Refresh aborted and will be retried in the future");
				didRefresh = false;
			}
			catch (FailedDeserializationException e)
			{
				environment.logToConsole("Followee data appears to be corrupt: " + e.getLocalizedMessage());
				environment.logToConsole("Refresh aborted and will be retried in the future");
				didRefresh = false;
			}
			newRoot = didRefresh
					? indexRoot
					: lastRoot
			;
			isSuccess = didRefresh;
		}
		else
		{
			// We couldn't resolve them so just advance the poll time.
			environment.logToConsole("Could not find followee");
			newRoot = lastRoot;
		}
		long lastPollMillis = System.currentTimeMillis();
		followees.updateExistingFollowee(publicKey, newRoot, lastPollMillis);
		
		if (isSuccess)
		{
			log.finish("Refresh successful!");
		}
		else
		{
			log.finish("Refresh failed!");
		}
	}
}
