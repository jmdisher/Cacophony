package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.FolloweeRefreshLogic;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.StandardRefreshSupport;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, SizeConstraintException, UsageException, FailedDeserializationException
	{
		IOperationLog log = environment.logOperation("Cleaning up to stop following " + _publicKey + "...");
		
		FollowIndex followIndex = access.readWriteFollowIndex();
		
		long currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
		
		// Removed the cache record and verify that we are following them.
		FollowRecord finalRecord = followIndex.checkoutRecord(_publicKey);
		if (null == finalRecord)
		{
			throw new UsageException("Not following public key: " + _publicKey.toPublicKey());
		}
		
		// Prepare for the cleanup.
		GlobalPrefs prefs = access.readGlobalPrefs();
		StandardRefreshSupport refreshSupport = new StandardRefreshSupport(environment, access);
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
		log.finish("Cleanup complete.  No longer following " + _publicKey);
	}
}
