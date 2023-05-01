package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListRecommendationsCommand(IpfsKey _targetKey) implements ICommand<KeyList>
{
	@Override
	public KeyList runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		IpfsKey[] keys;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			keys = _runCore(context.logger, context.publicKey, access);
		}
		return new KeyList("Recommending", keys);
	}


	private IpfsKey[] _runCore(ILogger logger, IpfsKey ourKey, IReadingAccess access) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		
		// If there was no key specified, or the key is our key, we can avoid key resolving.
		if ((null == _targetKey) || ourKey.equals(_targetKey))
		{
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		else
		{
			// See if this is a follower so we know if the data should be cached.
			IFolloweeReading followees = access.readableFolloweeData();
			rootToLoad = followees.getLastFetchedRootForFollowee(_targetKey);
			if (null != rootToLoad)
			{
				logger.logVerbose("Following " + _targetKey);
				isCached = true;
			}
			else
			{
				logger.logVerbose("NOT following " + _targetKey);
				rootToLoad = access.resolvePublicKey(_targetKey).get();
				// Throws KeyException on failure.
				Assert.assertTrue(null != rootToLoad);
			}
		}
		
		// Read the existing recommendations list.
		ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, isCached);
		StreamRecommendations recommendations = reader.loadRecommendations();
		
		// Note that we will filter the keys since some of them could be invalid.
		List<String> rawKeys = recommendations.getUser();
		return rawKeys.stream()
				.map(
						(String raw) -> IpfsKey.fromPublicKey(raw)
				)
				.filter(
						(IpfsKey key) -> (null != key)
				)
				.collect(Collectors.toList()).toArray(
						(int size) -> new IpfsKey[size]
				);
	}
}
