package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;


public record Opcode_SetPinnedCount(IpfsFile cid, int count) implements IDataOpcode, Serializable
{
	private static final long serialVersionUID = 1L;

	@Override
	public void apply(OpcodeContext context)
	{
		context.misc().setPinnedCount(cid, count);
	}
}
