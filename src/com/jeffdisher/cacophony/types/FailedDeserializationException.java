package com.jeffdisher.cacophony.types;


/**
 * This exception is used in the case where a piece of meta-data couldn't be decoded since it appeared to be malformed.
 * This typically means that the wrong kind of data was referenced.
 */
public class FailedDeserializationException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	public FailedDeserializationException(Class<?> expectedType)
	{
		super("Data could not be deserialized as " + expectedType.getName());
	}
}
