package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand<ChannelDescription>
{
	@Override
	public ChannelDescription runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		// We start by checking for known users.  We will either check local data or the known user cache, if we have one.  They are equivalent.
		ChannelDescription result = (null != context.userInfoCache)
				? _checkKnownUserCache(context)
				: _checkKnownUsers(context)
		;
		
		// If we don't know anything about this user, read the network.
		if (null == result)
		{
			result = _checkNetwork(context);
		}
		return result;
	}


	private ChannelDescription _checkKnownUserCache(ICommand.Context context)
	{
		// We have a cache when running in interactive mode.
		context.logger.logVerbose("Check known user cache: " + _channelPublicKey);
		ChannelDescription result = null;
		LocalUserInfoCache.Element cached = context.userInfoCache.getUserInfo(_channelPublicKey);
		if (null != cached)
		{
			IpfsFile userPicCid = cached.userPicCid();
			String userPicUrl = context.baseUrl + userPicCid.toSafeString();
			result = new ChannelDescription(null
					, cached.name()
					, cached.description()
					, userPicCid
					, cached.emailOrNull()
					, cached.websiteOrNull()
					, userPicUrl
			);
		}
		return result;
	}

	private ChannelDescription _checkKnownUsers(ICommand.Context context) throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		// We don't have a cache when running in the direct command-line mode.
		context.logger.logVerbose("Check known users directly: " + _channelPublicKey);
		ChannelDescription result = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile rootToLoad = null;
			// First is to see if this is us.
			if ((null == _channelPublicKey) || _channelPublicKey.equals(context.publicKey))
			{
				rootToLoad = access.getLastRootElement();
			}
			else
			{
				// Second, check if this is someone we are following.
				rootToLoad = access.readableFolloweeData().getLastFetchedRootForFollowee(_channelPublicKey);
			}
			if (null != rootToLoad)
			{
				// We know that this user is cached.
				ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, true);
				StreamDescription description = reader.loadDescription();
				String userPicUrl = context.baseUrl + description.getPicture();
				result = new ChannelDescription(null
						, description.getName()
						, description.getDescription()
						, IpfsFile.fromIpfsCid(description.getPicture())
						, description.getEmail()
						, description.getWebsite()
						, userPicUrl
				);
			}
		}
		return result;
	}

	private ChannelDescription _checkNetwork(ICommand.Context context) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		context.logger.logVerbose("Check network: " + _channelPublicKey);
		ChannelDescription result;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			// Read the existing recommendations list.
			IpfsFile rootToLoad = access.resolvePublicKey(_channelPublicKey).get();
			ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, false);
			StreamDescription description = reader.loadDescription();
			String userPicUrl = context.baseUrl + description.getPicture();
			result = new ChannelDescription(null
					, description.getName()
					, description.getDescription()
					, IpfsFile.fromIpfsCid(description.getPicture())
					, description.getEmail()
					, description.getWebsite()
					, userPicUrl
			);
		}
		return result;
	}
}
