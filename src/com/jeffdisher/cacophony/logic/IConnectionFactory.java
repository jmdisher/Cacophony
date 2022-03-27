package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * Interface to create the IConnection objects as we unify application and testing config code.
 */
public interface IConnectionFactory
{
	IConnection buildConnection(String ipfsHost) throws IpfsConnectionException;
}
