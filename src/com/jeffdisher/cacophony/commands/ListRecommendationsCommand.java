package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListRecommendationsCommand(IpfsKey _targetKey) implements ICommand<KeyList>
{
	@Override
	public KeyList runInContext(Context context) throws IpfsConnectionException, KeyException, ProtocolDataException, UsageException
	{
		// First, make sure that we find the relevant key (since they default to the local user).
		IpfsKey keyToCheck = (null != _targetKey)
				? _targetKey
				: context.getSelectedKey()
		;
		if (null == keyToCheck)
		{
			throw new UsageException("The default user cannot be used since they don't exist");
		}
		
		// We start by checking for known users.
		AbstractRecommendations recommendations = _checkKnownUsers(context, keyToCheck);
		
		// If we don't know anything about this user, take the explicit read-through cache (requires write-access).
		if (null == recommendations)
		{
			recommendations = _checkExplicitCache(context, keyToCheck);
		}
		
		// If there was an error, it would have thrown.
		Assert.assertTrue(null != recommendations);
		// Note that we will filter the keys since some of them could be invalid.
		List<IpfsKey> rawKeys = recommendations.getUserList();
		IpfsKey[] keys = rawKeys.stream()
				.filter(
						(IpfsKey key) -> (null != key)
				)
				.collect(Collectors.toList()).toArray(
						(int size) -> new IpfsKey[size]
				);
		return new KeyList("Recommending", keys);
	}


	private AbstractRecommendations _checkKnownUsers(Context context, IpfsKey keyToCheck) throws IpfsConnectionException, ProtocolDataException
	{
		// We don't have a cache when running in the direct command-line mode.
		context.logger.logVerbose("Check known users directly: " + keyToCheck);
		AbstractRecommendations result = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile rootToLoad = null;
			// First is to see if this is us.
			if (keyToCheck.equals(context.getSelectedKey()))
			{
				rootToLoad = access.getLastRootElement();
			}
			else
			{
				// Second, check if this is someone we are following.
				rootToLoad = access.readableFolloweeData().getLastFetchedRootForFollowee(keyToCheck);
			}
			if (null != rootToLoad)
			{
				// We know that this user is cached.
				ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, true);
				result = reader.loadRecommendations();
			}
		}
		return result;
	}

	private AbstractRecommendations _checkExplicitCache(Context context, IpfsKey keyToCheck) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		context.logger.logVerbose("Check explicit cache: " + keyToCheck);
		// Consult the cache - this never returns null but will throw on error.
		ExplicitCacheData.UserInfo userInfo = context.getExplicitCache().loadUserInfo(keyToCheck).get();
		
		AbstractRecommendations result;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// Everything in the explicit cache is cached.
			result = access.loadCached(userInfo.recommendationsCid(), AbstractRecommendations.DESERIALIZER).get();
		}
		return result;
	}
}
