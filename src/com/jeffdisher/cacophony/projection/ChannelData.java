package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_DefineChannel;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.Pair;


/**
 * The data projection, containing information about the home channels.
 */
public class ChannelData
{
	/**
	 * @return An empty instance.
	 */
	public static ChannelData create()
	{
		return new ChannelData();
	}


	private final Map<String, Pair<IpfsKey, IpfsFile>> _homeChannelsByKeyName;

	private ChannelData()
	{
		_homeChannelsByKeyName = new HashMap<>();
	}

	/**
	 * Serializes all the data in the receiver to the given writer.
	 * 
	 * @param writer The opcode writer which will be used to serialize the data.
	 * @throws IOException There was an error writing the data.
	 */
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

	/**
	 * Adds a new channel with the given initial state.
	 * 
	 * @param keyName The name of the key on the local IPFS node.
	 * @param publicKey The public key of the user.
	 * @param rootElement The CID of their root data element.
	 */
	public void initializeChannelState(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		Assert.assertTrue(!_homeChannelsByKeyName.containsKey(keyName));
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != rootElement);
		_homeChannelsByKeyName.put(keyName, new Pair<>(publicKey, rootElement));
	}

	/**
	 * @return The list of key names of all home channels.
	 */
	public Set<String> getKeyNames()
	{
		return _homeChannelsByKeyName.keySet();
	}

	/**
	 * @param keyName The key name to look up.
	 * @return The public key of this home user or null, if they are not known.
	 */
	public IpfsKey getPublicKey(String keyName)
	{
		// If we haven't yet created the channel, we won't have a key for it.
		Pair<IpfsKey, IpfsFile> pair = _homeChannelsByKeyName.get(keyName);
		return (null != pair)
				? pair.first()
				: null
		;
	}

	/**
	 * @param keyName The key name to look up.
	 * @return The CID of this user's last published root, null if they are not known.
	 */
	public IpfsFile getLastPublishedIndex(String keyName)
	{
		// Note that we may try to look up keys which haven't yet been published.
		return _homeChannelsByKeyName.containsKey(keyName)
				? _homeChannelsByKeyName.get(keyName).second()
				: null
		;
	}

	/**
	 * Updates the published index for an existing home user.
	 * 
	 * @param keyName The name of the user to update.
	 * @param publicKey The public key of this user.
	 * @param rootElement The new root element for this user.
	 */
	public void setLastPublishedIndex(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		Assert.assertTrue(null != keyName);
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != rootElement);
		_homeChannelsByKeyName.put(keyName, new Pair<>(publicKey, rootElement));
	}

	/**
	 * Removes the home channel user with the given key name.
	 * 
	 * @param keyName The key name of the user to remove.
	 */
	public void removeChannel(String keyName)
	{
		// We expect this to be here.
		Assert.assertTrue(_homeChannelsByKeyName.containsKey(keyName));
		_homeChannelsByKeyName.remove(keyName);
	}
}
