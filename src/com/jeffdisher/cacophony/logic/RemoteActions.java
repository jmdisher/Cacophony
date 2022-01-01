package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;

import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;


public class RemoteActions
{
	private final IPFS _ipfs;

	public RemoteActions(IPFS ipfs)
	{
		_ipfs = ipfs;
	}

	public StreamIndex loadIndex(String publicKey)
	{
		// TODO Auto-generated method stub
		return null;
	}

	public Multihash saveIndex(StreamDescription description)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
