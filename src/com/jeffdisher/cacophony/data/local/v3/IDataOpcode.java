package com.jeffdisher.cacophony.data.local.v3;


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
