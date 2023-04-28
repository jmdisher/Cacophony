package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.jeffdisher.cacophony.data.local.v2.Opcode_CreateChannel;
import com.jeffdisher.cacophony.data.local.v2.Opcode_SetLastPublishedIndex;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public class ChannelData
{
	public static ChannelData create()
	{
		return new ChannelData();
	}


	private String _ipfsHost;
	private String _keyName;
	private IpfsFile _lastPublishedIndex;

	private ChannelData()
	{
	}

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		stream.writeObject(new Opcode_CreateChannel(_ipfsHost, _keyName));
		if (null != _lastPublishedIndex)
		{
			stream.writeObject(new Opcode_SetLastPublishedIndex(_lastPublishedIndex));
		}
	}

	public void initializeChannelState(String ipfsHost, String keyName)
	{
		// This is partially a V3 shape but we still want to apply the V2 rules since that is what we are using.
		Assert.assertTrue(null == _ipfsHost);
		Assert.assertTrue(null == _keyName);
		_ipfsHost = ipfsHost;
		_keyName = keyName;
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
