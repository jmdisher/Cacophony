package com.jeffdisher.cacophony.data.local.v4;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;


/**
 * The abstract interface common to all data opcodes.
 */
public interface IDataOpcode
{
	/**
	 * @return The opcode type used when serializing the opcode.
	 */
	OpcodeType type();

	/**
	 * Applies the meaning of the opcode to the given context (Version 3 only).  This is essentially "executing the
	 * opcode".
	 * 
	 * @param context The container of the V3 data state objects.
	 */
	void applyV3(OpcodeContextV3 context);

	/**
	 * Applies the meaning of the opcode to the given context.  This is essentially "executing the opcode".
	 * 
	 * @param context The container of the data state objects.
	 */
	void apply(OpcodeContext context);

	/**
	 * Requests that the opcode write itself to the given serializer.
	 * 
	 * @param serializer The mechanism for serializing the opcode elements.
	 */
	void write(OpcodeSerializer serializer);
}
