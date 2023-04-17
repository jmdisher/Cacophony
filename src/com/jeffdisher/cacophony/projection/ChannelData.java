package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v2.Opcode_CreateChannel;
import com.jeffdisher.cacophony.data.local.v2.Opcode_SetLastPublishedIndex;
import com.jeffdisher.cacophony.types.IpfsFile;


public class ChannelData
{
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

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		stream.writeObject(new Opcode_CreateChannel(_ipfsHost, _keyName));
		if (null != _lastPublishedIndex)
		{
			stream.writeObject(new Opcode_SetLastPublishedIndex(_lastPublishedIndex));
		}
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
