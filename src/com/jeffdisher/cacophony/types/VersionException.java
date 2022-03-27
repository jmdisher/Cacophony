package com.jeffdisher.cacophony.types;


/**
 * This exception is used in the case where the local config data is of a version which cannot be understood by this
 * version of Cacophony.
 */
public class VersionException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	public VersionException(String message)
	{
		super(message);
	}
}
