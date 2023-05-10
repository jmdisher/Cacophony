package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListRecommendationsCommand(IpfsKey _targetKey) implements ICommand<KeyList>
{
	@Override
	public KeyList runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		// We start by checking for known users.
		StreamRecommendations recommendations = _checkKnownUsers(context);
		
		// If we don't know anything about this user, read the network.
		if (null == recommendations)
		{
			recommendations = _checkNetwork(context);
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


	private StreamRecommendations _checkKnownUsers(ICommand.Context context) throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		// We don't have a cache when running in the direct command-line mode.
		context.logger.logVerbose("Check known users directly: " + _targetKey);
		StreamRecommendations result = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile rootToLoad = null;
			// First is to see if this is us.
			if ((null == _targetKey) || _targetKey.equals(context.publicKey))
			{
				rootToLoad = access.getLastRootElement();
			}
			else
			{
				// Second, check if this is someone we are following.
				rootToLoad = access.readableFolloweeData().getLastFetchedRootForFollowee(_targetKey);
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

	private StreamRecommendations _checkNetwork(ICommand.Context context) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		context.logger.logVerbose("Check network: " + _targetKey);
		StreamRecommendations result;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			// Read the existing recommendations list.
			IpfsFile rootToLoad = access.resolvePublicKey(_targetKey).get();
			ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, false);
			result = reader.loadRecommendations();
		}
		return result;
	}
}
