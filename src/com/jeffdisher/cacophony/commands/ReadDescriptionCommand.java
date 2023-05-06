package com.jeffdisher.cacophony.commands;

import java.net.URL;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
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


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand<ChannelDescription>
{
	@Override
	public ChannelDescription runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		ChannelDescription result;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			result = _runCore(context.logger, context.baseUrl, access, context.publicKey);
		}
		return result;
	}


	private ChannelDescription _runCore(ILogger logger, URL baseUrl, IReadingAccess access, IpfsKey ourPublicKey) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if ((null != _channelPublicKey) && !_channelPublicKey.equals(ourPublicKey))
		{
			// This is an explicit lookup which is not us.
			IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_channelPublicKey);
			if (null != lastRoot)
			{
				logger.logVerbose("Following " + _channelPublicKey);
				rootToLoad = lastRoot;
				isCached = true;
			}
			else
			{
				logger.logVerbose("NOT following " + _channelPublicKey);
				rootToLoad = access.resolvePublicKey(_channelPublicKey).get();
				// Throws KeyException on failure.
				Assert.assertTrue(null != rootToLoad);
				isCached = false;
			}
		}
		else
		{
			// This is either defaulting to us (no channel) or we were explicitly asked about us.
			// Just list our recommendations.
			// Read the existing StreamIndex.
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
		}
		ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, isCached);
		StreamDescription description = reader.loadDescription();
		String userPicUrl = baseUrl + description.getPicture();
		return new ChannelDescription(null
				, description.getName()
				, description.getDescription()
				, IpfsFile.fromIpfsCid(description.getPicture())
				, description.getEmail()
				, description.getWebsite()
				, userPicUrl
		);
	}
}
