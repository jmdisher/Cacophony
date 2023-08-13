package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.caches.ILocalUserInfoCache;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record ListChannelsCommand() implements ICommand<ListChannelsCommand.ChannelList>
{
	@Override
	public ChannelList runInContext(Context context) throws IpfsConnectionException
	{
		// We want a pretty low-level read which doesn't take into account the current context key so use the data model, directly.
		LocalDataModel dataModel = context.sharedDataModel;
		ChannelList list;
		try (IReadOnlyLocalData reading = dataModel.openForRead())
		{
			ChannelData data = reading.readLocalIndex();
			Set<String> keyNames = data.getKeyNames();
			OneChannel[] channels = keyNames.stream()
					.map((String keyName) -> {
						IpfsKey publicKey = data.getPublicKey(keyName);
						IpfsFile lastRoot = data.getLastPublishedIndex(keyName);
						boolean isSelected = publicKey.equals(context.getSelectedKey());
						// If we have a userInfoCache, populate the additional data we would see to describe them.
						String name = null;
						String userPicUrl = null;
						if (null != context.userInfoCache)
						{
							ILocalUserInfoCache.Element cached = context.userInfoCache.getUserInfo(publicKey);
							// While we _should_ always see this in the cache, if present, race conditions might mean we
							// don't but we at least want to observe that case, if it happens.
							Assert.assertTrue(null != cached);
							name = cached.name();
							if (null != cached.userPicCid())
							{
								userPicUrl = context.baseUrl + cached.userPicCid().toSafeString();
							}
						}
						return new OneChannel(keyName, publicKey, lastRoot, isSelected, name, userPicUrl);
					})
					.collect(Collectors.toList())
					.toArray((int size) -> new OneChannel[size])
			;
			list = new ChannelList(channels);
		}
		return list;
	}


	public static class ChannelList implements ICommand.Result
	{
		private final OneChannel[] _channels;
		
		public ChannelList(OneChannel[] channels)
		{
			_channels = channels;
		}
		@Override
		public IpfsFile getIndexToPublish()
		{
			// Nothing changed.
			return null;
		}
		@Override
		public void writeHumanReadable(PrintStream output)
		{
			output.println("Found " + _channels.length + " channels:");
			for (OneChannel channel : _channels)
			{
				output.println("Key name: " + channel.keyName + (channel.isSelected ? " (SELECTED)" : ""));
				output.println("\tPublic key: " + channel.publicKey);
				output.println("\tLast published root: " + channel.lastPublishedRoot);
				output.println("\tName: " + ((null != channel.optionalName) ? channel.optionalName : "(unknown)"));
				output.println("\tUser pic URL: " + ((null != channel.optionalUserPicUrl) ? channel.optionalUserPicUrl : "(unknown)"));
			}
		}
		public OneChannel[] getChannels()
		{
			return _channels;
		}
	}

	public static record OneChannel(String keyName
			, IpfsKey publicKey
			, IpfsFile lastPublishedRoot
			, boolean isSelected
			, String optionalName
			, String optionalUserPicUrl
	)
	{
	}
}
