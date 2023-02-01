package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.Map;

import com.jeffdisher.cacophony.types.IpfsConnectionException;

import io.ipfs.api.IPFS;
import io.ipfs.multiaddr.MultiAddress;


public class RealConnectionFactory implements IConnectionFactory
{
	// This is the default used by IPFS.java (timeout to establish connection.
	private static final int CONNECTION_TIMEOUT_MILLIS = 10_000;
	// The default wait for response in IPFS.java is 1 minute but pin could take a long time so we use 30 minutes.
	// (this value isn't based on any solid science so it may change in the future).
	private static final int LONG_READ_TIMEOUT_MILLIS = 30 * 60 * 1000;

	private final Uploader _uploader;

	public RealConnectionFactory(Uploader uploader)
	{
		_uploader = uploader;
	}

	@Override
	public IConnection buildConnection(String ipfsHost) throws IpfsConnectionException
	{
		try {
			IPFS defaultConnection = new IPFS(ipfsHost);
			@SuppressWarnings("unchecked")
			Map<String, Object> addresses = (Map<String, Object>) defaultConnection.config.get("Addresses");
			String result = (String) addresses.get("Gateway");
			// This "Gateway" is of the form:  /ip4/127.0.0.1/tcp/8080
			int gatewayPort = Integer.parseInt(result.split("/")[4]);
			
			MultiAddress addr = new MultiAddress(ipfsHost);
			IPFS longWaitConnection = new IPFS(addr.getHost(), addr.getTCPPort(), "/api/v0/", CONNECTION_TIMEOUT_MILLIS, LONG_READ_TIMEOUT_MILLIS, false);
			return new IpfsConnection(_uploader, defaultConnection, longWaitConnection, gatewayPort);
		}
		catch (IOException e)
		{
			// This happens if we fail to read the config, which should only happen if the node is bogus.
			throw new IpfsConnectionException("connect", ipfsHost, e);
		}
		catch (RuntimeException e)
		{
			// For some reason, "new IPFS" throws a RuntimeException, instead of IOException, if the connection fails.
			throw new IpfsConnectionException("connect", ipfsHost, new IOException(e));
		}
	}
}
