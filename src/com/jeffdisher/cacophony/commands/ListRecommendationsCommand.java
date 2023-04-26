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


public record ListRecommendationsCommand(IpfsKey _publicKey) implements ICommand<KeyList>
{
	@Override
	public KeyList runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		IpfsKey[] keys;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			keys = _runCore(context.logger, access);
		}
		return new KeyList("Recommending", keys);
	}


	private IpfsKey[] _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _publicKey)
		{
			rootToLoad = followees.getLastFetchedRootForFollowee(_publicKey);
			if (null != rootToLoad)
			{
				logger.logVerbose("Following " + _publicKey);
				isCached = true;
			}
			else
			{
				logger.logVerbose("NOT following " + _publicKey);
				rootToLoad = access.resolvePublicKey(_publicKey).get();
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _publicKey);
				}
			}
		}
		else
		{
			// Just list our recommendations.
			// Read the existing StreamIndex.
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
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
