package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record Opcode_AddFolloweeRecord(IpfsKey followeeKey, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes) implements IDataOpcode, Serializable
{
	private static final long serialVersionUID = 1L;

	@Override
	public void apply(OpcodeContext context)
	{
		context.followee().addElement(followeeKey, elementHash, imageHash, leafHash, combinedSizeBytes);
	}
}
