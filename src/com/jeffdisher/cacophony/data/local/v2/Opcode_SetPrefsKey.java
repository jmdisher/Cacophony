package com.jeffdisher.cacophony.data.local.v2;

import java.io.Serializable;


public record Opcode_SetPrefsKey(String keyName, Serializable value) implements IDataOpcode, Serializable
{
	private static final long serialVersionUID = 1L;

	@Override
	public void apply(OpcodeContext context)
	{
		context.misc().setPrefsKey(keyName, value);
	}
}
