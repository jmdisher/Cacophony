package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChannelDescription;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.logic.ExplicitCacheLogic;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand<ChannelDescription>
{
	@Override
	public ChannelDescription runInContext(Context context) throws IpfsConnectionException, KeyException, ProtocolDataException, UsageException
	{
		// First, make sure that we find the relevant key (since they default to the local user).
		IpfsKey keyToCheck = (null != _channelPublicKey)
				? _channelPublicKey
				: context.getSelectedKey()
		;
		if (null == keyToCheck)
		{
			throw new UsageException("The default user cannot be used since they don't exist");
		}
		
		// We start by checking for known users.  We will either check local data or the known user cache, if we have one.  They are equivalent.
		ChannelDescription result = (null != context.userInfoCache)
				? _checkKnownUserCache(context, keyToCheck)
				: _checkKnownUsers(context, keyToCheck)
		;
		
		// If we don't know anything about this user, take the explicit read-through cache (requires write-access).
		if (null == result)
		{
			result = _checkExplicitCache(context, keyToCheck);
		}
		return result;
	}


	private ChannelDescription _checkKnownUserCache(Context context, IpfsKey keyToCheck)
	{
		// We have a cache when running in interactive mode.
		context.logger.logVerbose("Check known user cache: " + keyToCheck);
		ChannelDescription result = null;
		LocalUserInfoCache.Element cached = context.userInfoCache.getUserInfo(keyToCheck);
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
					, cached.featureOrNull()
			);
		}
		return result;
	}

	private ChannelDescription _checkKnownUsers(Context context, IpfsKey keyToCheck) throws IpfsConnectionException, ProtocolDataException
	{
		// We don't have a cache when running in the direct command-line mode.
		context.logger.logVerbose("Check known users directly: " + keyToCheck);
		ChannelDescription result = null;
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
				AbstractDescription description = reader.loadDescription();
				String userPicUrl = context.baseUrl + description.getPicCid().toSafeString();
				result = new ChannelDescription(null
						, description.getName()
						, description.getDescription()
						, description.getPicCid()
						, description.getEmail()
						, description.getWebsite()
						, userPicUrl
						, description.getFeature()
				);
			}
		}
		return result;
	}

	private ChannelDescription _checkExplicitCache(Context context, IpfsKey keyToCheck) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		context.logger.logVerbose("Check explicit cache: " + keyToCheck);
		// Consult the cache - this never returns null but will throw on error.
		ExplicitCacheData.UserInfo userInfo = ExplicitCacheLogic.loadUserInfo(context, keyToCheck);
		ChannelDescription result;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// Everything in the explicit cache is cached.
			AbstractDescription description = access.loadCached(userInfo.descriptionCid(), AbstractDescription.DESERIALIZER).get();
			String userPicUrl = context.baseUrl + userInfo.userPicCid().toSafeString();
			result = new ChannelDescription(null
					, description.getName()
					, description.getDescription()
					, description.getPicCid()
					, description.getEmail()
					, description.getWebsite()
					, userPicUrl
					, description.getFeature()
			);
		}
		return result;
	}
}
