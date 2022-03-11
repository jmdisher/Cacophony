package com.jeffdisher.cacophony.logic;

import java.io.IOException;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
	public void add(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			_pin.add(cid.getMultihash());
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException(e);
		}
	}

	@Override
	public void rm(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			_pin.rm(cid.getMultihash());
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException(e);
		}
	}
}
