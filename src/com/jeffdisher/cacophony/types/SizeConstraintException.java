package com.jeffdisher.cacophony.types;


/**
 * This exception is used in the case where a piece of meta-data couldn't be loaded since it was considered too large
 * for the protocol constraints {@link com.jeffdisher.cacophony.utils.SizeLimits}.
 */
public class SizeConstraintException extends ProtocolDataException
{
	private static final long serialVersionUID = 1L;

	public SizeConstraintException(String limitType, long givenSize, long limitSize)
	{
		super("Size limit broken: " + limitType + " was " + givenSize + " but is limited to " + limitSize);
	}
}
