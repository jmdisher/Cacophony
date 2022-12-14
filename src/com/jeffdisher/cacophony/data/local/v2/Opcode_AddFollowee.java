package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record Opcode_AddFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis) implements IDataOpcode, Serializable
{
	private static final long serialVersionUID = 1L;

	@Override
	public void apply(OpcodeContext context)
	{
		context.followee().createNewFollowee(followeeKey, indexRoot, lastPollMillis);
	}
}
