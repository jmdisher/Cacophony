package com.jeffdisher.cacophony.projection;

import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.types.IpfsFile;


public class ChannelData
{
	public static ChannelData buildOnIndex(LocalIndex index)
	{
		return new ChannelData(index.ipfsHost(), index.keyName(), index.lastPublishedIndex());
	}


	private String _ipfsHost;
	private String _keyName;
	private IpfsFile _lastPublishedIndex;

	private ChannelData(String ipfsHost, String keyName, IpfsFile lastPublishedIndex)
	{
		_ipfsHost = ipfsHost;
		_keyName = keyName;
		_lastPublishedIndex = lastPublishedIndex;
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
