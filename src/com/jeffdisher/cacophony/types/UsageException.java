package com.jeffdisher.cacophony.types;


/**
 * This exception type is used when a command has been told to do something invalid in ways which couldn't be detected
 * during command-line parsing.
 */
public class UsageException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	public UsageException(String message)
	{
		super(message);
	}
}
