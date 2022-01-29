package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.types.IpfsFile;

import io.ipfs.api.IPFS.Pin;


public class IpfsPinMechanism implements IPinMechanism
{
	private final Pin _pin;

	public IpfsPinMechanism(Pin pin)
	{
		_pin = pin;
	}

	@Override
	public void add(IpfsFile cid) throws IOException
	{
		_pin.add(cid.cid());
	}

	@Override
	public void rm(IpfsFile cid) throws IOException
	{
		_pin.rm(cid.cid());
	}
}
