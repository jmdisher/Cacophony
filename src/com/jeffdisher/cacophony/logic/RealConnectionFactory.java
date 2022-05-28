package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.util.Map;

import com.jeffdisher.cacophony.types.IpfsConnectionException;

import io.ipfs.api.IPFS;


public class RealConnectionFactory implements IConnectionFactory
{
	@Override
	public IConnection buildConnection(String ipfsHost) throws IpfsConnectionException
	{
		try {
			IPFS ipfs = new IPFS(ipfsHost);
			@SuppressWarnings("unchecked")
			Map<String, Object> addresses = (Map<String, Object>) ipfs.config.get("Addresses");
			String result = (String) addresses.get("Gateway");
			// This "Gateway" is of the form:  /ip4/127.0.0.1/tcp/8080
			int gatewayPort = Integer.parseInt(result.split("/")[4]);
			return new IpfsConnection(ipfs, gatewayPort);
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
