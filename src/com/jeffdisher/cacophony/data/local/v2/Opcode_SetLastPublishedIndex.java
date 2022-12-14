package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;

import com.jeffdisher.cacophony.types.IpfsFile;


public record Opcode_SetLastPublishedIndex(IpfsFile lastPublishedIndex) implements IDataOpcode, Serializable
{
	private static final long serialVersionUID = 1L;

	@Override
	public void apply(OpcodeContext context)
	{
		context.misc().setLastPublishedIndex(lastPublishedIndex);
	}
}
