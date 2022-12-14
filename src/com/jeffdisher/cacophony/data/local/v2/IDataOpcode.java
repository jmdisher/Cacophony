package com.jeffdisher.cacophony.data.local.v2;


/**
 * The abstract interface common to all data opcodes.
 */
public interface IDataOpcode
{
	/**
	 * Called to apply the opcode to the data model managed by OpcodeContext.
	 * 
	 * @param context The abstraction of the data model.
	 */
	void apply(OpcodeContext context);
}
