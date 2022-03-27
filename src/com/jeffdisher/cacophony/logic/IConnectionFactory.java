package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * Interface to create the IConnection objects since we now use a common config object for the real system and unit
 * tests.
 */
public interface IConnectionFactory
{
	IConnection buildConnection(String ipfsHost) throws IpfsConnectionException;
}
