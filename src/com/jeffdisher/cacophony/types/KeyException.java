package com.jeffdisher.cacophony.types;


/**
 * This exception type is used when an IPNS key resolution failure occurs (typically meaning the system doesn't know of
 * the key).
 */
public class KeyException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	public KeyException(IpfsKey failedResolve, IpfsConnectionException networkException)
	{
		super("Failed to resolve: " + failedResolve, networkException);
	}
}
