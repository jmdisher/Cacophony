package com.jeffdisher.cacophony.types;


/**
 * Superclass of all Cacophony's internal exceptions.
 */
public class CacophonyException extends Exception
{
	private static final long serialVersionUID = 1L;

	public CacophonyException(String message)
	{
		super(message);
	}

	public CacophonyException(String message, Exception exception)
	{
		super(message, exception);
	}
}
