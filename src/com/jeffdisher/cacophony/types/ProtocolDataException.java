package com.jeffdisher.cacophony.types;


/**
 * This exception is the common base class of the kinds of exceptions which describe a failure to interpret data
 * according to the Cacophony protocol.  This typically involves things like XML schema violations or size limit
 * violations.
 * This class is provided so that lower-level components can use the more specific exceptions while high-level
 * components only need to know that "the data was readable but wrong".
 */
public abstract class ProtocolDataException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	protected ProtocolDataException(String message)
	{
		super(message);
	}
}
