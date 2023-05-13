package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ExplicitCacheLogic;
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
	public KeyList runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, ProtocolDataException, UsageException
	{
		// First, make sure that we find the relevant key (since they default to the local user).
		IpfsKey keyToCheck = (null != _targetKey)
				? _targetKey
				: context.publicKey
		;
		if (null == keyToCheck)
		{
			throw new UsageException("The default user cannot be used since they don't exist");
		}
		
		// We start by checking for known users.
		StreamRecommendations recommendations = _checkKnownUsers(context, keyToCheck);
		
		// If we don't know anything about this user, take the explicit read-through cache (requires write-access).
		if (null == recommendations)
		{
			recommendations = _checkExplicitCache(context, keyToCheck);
		}
		
		// If there was an error, it would have thrown.
		Assert.assertTrue(null != recommendations);
		// Note that we will filter the keys since some of them could be invalid.
		List<String> rawKeys = recommendations.getUser();
		IpfsKey[] keys = rawKeys.stream()
				.map(
						(String raw) -> IpfsKey.fromPublicKey(raw)
				)
				.filter(
						(IpfsKey key) -> (null != key)
				)
				.collect(Collectors.toList()).toArray(
						(int size) -> new IpfsKey[size]
				);
		return new KeyList("Recommending", keys);
	}


	private StreamRecommendations _checkKnownUsers(ICommand.Context context, IpfsKey keyToCheck) throws IpfsConnectionException, ProtocolDataException
	{
		// We don't have a cache when running in the direct command-line mode.
		context.logger.logVerbose("Check known users directly: " + keyToCheck);
		StreamRecommendations result = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile rootToLoad = null;
			// First is to see if this is us.
			if (keyToCheck.equals(context.publicKey))
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

	private StreamRecommendations _checkExplicitCache(ICommand.Context context, IpfsKey keyToCheck) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		context.logger.logVerbose("Check explicit cache: " + keyToCheck);
		StreamRecommendations result;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// Consult the cache - this never returns null but will throw on error.
			ExplicitCacheData.UserInfo userInfo = ExplicitCacheLogic.loadUserInfo(access, keyToCheck);
			// Everything in the explicit cache is cached.
			result = access.loadCached(userInfo.recommendationsCid(), (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		}
		return result;
	}
}
