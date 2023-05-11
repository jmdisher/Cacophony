package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v2.Opcode_CreateChannel;
import com.jeffdisher.cacophony.data.local.v2.Opcode_SetLastPublishedIndex;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.Opcode_DefineChannel;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.Pair;


public class ChannelData
{
	public static ChannelData create()
	{
		return new ChannelData();
	}


	private final Map<String, Pair<IpfsKey, IpfsFile>> _homeChannelsByKeyName;

	private ChannelData()
	{
		_homeChannelsByKeyName = new HashMap<>();
	}

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		// V2 data model only works with a single channel.
		Assert.assertTrue(1 == _homeChannelsByKeyName.size());
		String keyName = _homeChannelsByKeyName.keySet().iterator().next();
		IpfsFile lastPublishedIndex = _homeChannelsByKeyName.get(keyName).second();
		stream.writeObject(new Opcode_CreateChannel("ipfs", keyName));
		if (null != lastPublishedIndex)
		{
			stream.writeObject(new Opcode_SetLastPublishedIndex(lastPublishedIndex));
		}
	}

	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		for (Map.Entry<String, Pair<IpfsKey, IpfsFile>> elt : _homeChannelsByKeyName.entrySet())
		{
			String keyName = elt.getKey();
			Pair<IpfsKey, IpfsFile> channelInfo = elt.getValue();
			IpfsKey publicKey = channelInfo.first();
			IpfsFile rootElement = channelInfo.second();
			writer.writeOpcode(new Opcode_DefineChannel(keyName, publicKey, rootElement));
		}
	}

	public void initializeChannelState(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		Assert.assertTrue(!_homeChannelsByKeyName.containsKey(keyName));
		_homeChannelsByKeyName.put(keyName, new Pair<>(publicKey, rootElement));
	}

	public Set<String> getKeyNames()
	{
		return _homeChannelsByKeyName.keySet();
	}

	public IpfsKey getPublicKey(String keyName)
	{
		// If we haven't yet created the channel, we won't have a key for it.
		Pair<IpfsKey, IpfsFile> pair = _homeChannelsByKeyName.get(keyName);
		return (null != pair)
				? pair.first()
				: null
		;
	}

	public IpfsFile getLastPublishedIndex(String keyName)
	{
		// Note that we may try to look up keys which haven't yet been published.
		return _homeChannelsByKeyName.containsKey(keyName)
				? _homeChannelsByKeyName.get(keyName).second()
				: null
		;
	}

	public void setLastPublishedIndex(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		_homeChannelsByKeyName.put(keyName, new Pair<>(publicKey, rootElement));
	}
}
