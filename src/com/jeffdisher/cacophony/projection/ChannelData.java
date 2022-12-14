package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsFile;


public class ChannelData
{
	public static ChannelData buildOnIndex(LocalIndex index)
	{
		ChannelData data = new ChannelData(index.ipfsHost(), index.keyName());
		data.setLastPublishedIndex(index.lastPublishedIndex());
		return data;
	}

	public static ChannelData create(String ipfsHost, String keyName)
	{
		return new ChannelData(ipfsHost, keyName);
	}


	private String _ipfsHost;
	private String _keyName;
	private IpfsFile _lastPublishedIndex;

	private ChannelData(String ipfsHost, String keyName)
	{
		_ipfsHost = ipfsHost;
		_keyName = keyName;
	}

	public LocalIndex serializeToIndex()
	{
		return new LocalIndex(_ipfsHost, _keyName, _lastPublishedIndex);
	}

	public String ipfsHost()
	{
		return _ipfsHost;
	}

	public String keyName()
	{
		return _keyName;
	}

	public IpfsFile lastPublishedIndex()
	{
		return _lastPublishedIndex;
	}

	public void setLastPublishedIndex(IpfsFile lastPublishedIndex)
	{
		_lastPublishedIndex = lastPublishedIndex;
	}
}
