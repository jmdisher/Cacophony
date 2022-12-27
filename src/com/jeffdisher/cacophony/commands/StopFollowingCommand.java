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


public record StopFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, UsageException
	{
		IOperationLog log = environment.logOperation("Cleaning up to stop following " + _publicKey + "...");
		
		IFolloweeWriting followees = access.writableFolloweeData();
		
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followees);
		
		// Removed the cache record and verify that we are following them.
		IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_publicKey);
		if (null == lastRoot)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		// Prepare for the cleanup.
		PrefsData prefs = access.readPrefs();
		boolean didRefresh = false;
		try
		{
			didRefresh = CommandHelpers.doRefreshOfRecord(environment, access, followees, _publicKey, currentCacheUsageInBytes, lastRoot, null, prefs);
		}
		catch (IpfsConnectionException e)
		{
			throw e;
		}
		catch (SizeConstraintException e)
		{
			// We don't expect this in unfollow.
			throw Assert.unexpected(e);
		}
		catch (FailedDeserializationException e)
		{
			// We don't expect this in unfollow.
			throw Assert.unexpected(e);
		}
		// There is no real way to fail at this refresh since we are just dropping things.
		Assert.assertTrue(didRefresh);
		followees.removeFollowee(_publicKey);
		
		// TODO: Determine if we want to handle unfollow errors as just log operations or if we should leave them as fatal.
		log.finish("Cleanup complete.  No longer following " + _publicKey);
	}
}
