package com.jeffdisher.cacophony.types;

import java.io.IOException;


/**
 * This exception is used when something unexpectedly bizarre happens with the IPFS connection.
 * This is unfortunately common where it appears as though many interactions with the IPFS daemon unexpectedly time out
 * so it may be possible that most of these cases can be re-attempted or the connection rebuilt and then retried.
 * Some IPFS timeouts are unavoidable since it is possible to ask for a CID which doesn't exist but the system will
 * search until timeout.
 */
public class IpfsConnectionException extends CacophonyException
{
	private static final long serialVersionUID = 1L;

	public IpfsConnectionException(IOException underlyingException)
	{
		super("IPFS connection malfunctioning", underlyingException);
	}
}
