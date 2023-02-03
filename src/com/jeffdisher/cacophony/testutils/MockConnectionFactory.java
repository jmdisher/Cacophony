package com.jeffdisher.cacophony.testutils;

import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IConnectionFactory;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


public class MockConnectionFactory implements IConnectionFactory
{
	private final IConnection _connection;

	public MockConnectionFactory(IConnection connection)
	{
		_connection = connection;
	}

	@Override
	public IConnection buildConnection(String ipfsHost) throws IpfsConnectionException
	{
		return _connection;
	}
}
