package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;


public class IpfsConnection implements IConnection
{
	private final IPFS _connection;
	private final int _gatewayPort;

	public IpfsConnection(IPFS connection, int gatewayPort)
	{
		_connection = connection;
		_gatewayPort = gatewayPort;
	}

	@Override
	public List<Key> getKeys() throws IpfsConnectionException
	{
		try
		{
			return _connection.key.list().stream().map((info) -> new IConnection.Key(info.name, new IpfsKey(info.id))).collect(Collectors.toList());
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("getKeys", "", e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("getKeys", "", e);
		}
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException
	{
		try
		{
			NamedStreamable.InputStreamWrapper wrapper = new NamedStreamable.InputStreamWrapper(dataStream);
			
			List<MerkleNode> nodes = _connection.add(wrapper);
			// Even with larger files, this only returns a single element, so it isn't obvious why this is a list.
			Assert.assertTrue(1 == nodes.size());
			Multihash hash = nodes.get(0).hash;
			
			return new IpfsFile(hash);
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("store", "stream", e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("store", "stream", e);
		}
	}

	@Override
	public byte[] loadData(IpfsFile file) throws IpfsConnectionException
	{
		try
		{
			return _connection.cat(file.getMultihash());
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("load", file, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("load", file, e);
		}
	}

	@Override
	public void publish(String keyName, IpfsFile file) throws IpfsConnectionException
	{
		try
		{
			Map<?,?> map = _connection.name.publish(file.getMultihash(), Optional.of(keyName));
			String value = (String) map.get("Value");
			String index58 = file.toSafeString();
			Assert.assertTrue(value.substring(value.lastIndexOf("/") + 1).equals(index58));
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("publish", file, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("publish", file, e);
		}
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IpfsConnectionException
	{
		try
		{
			String publishedPath = _connection.name.resolve(key.getMultihash());
			String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
			return IpfsFile.fromIpfsCid(published);
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("resolve", key, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("resolve", key, e);
		}
	}

	@Override
	public long getSizeInBytes(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			Map<?,?> dataMap = _connection.file.ls(cid.getMultihash());
			return ((Number)((Map<?,?>)((Map<?,?>)dataMap.get("Objects")).get(cid.toSafeString())).get("Size")).longValue();
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("getSize", cid, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("getSize", cid, e);
		}
	}

	@Override
	public URL urlForDirectFetch(IpfsFile cid)
	{
		try
		{
			return new URL(_connection.protocol, _connection.host, _gatewayPort, "/ipfs/" + cid.toSafeString());
		}
		catch (MalformedURLException e)
		{
			throw Assert.unexpected(e);
		}
	}

	@Override
	public void pin(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			_connection.pin.add(cid.getMultihash());
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("pin", cid, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("pin", cid, e);
		}
	}

	@Override
	public void rm(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			_connection.pin.rm(cid.getMultihash());
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("rm", cid, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("rm", cid, e);
		}
	}

	@Override
	public Key generateKey(String keyName) throws IpfsConnectionException
	{
		try
		{
			KeyInfo info = _connection.key.gen(keyName, Optional.empty(), Optional.empty());
			return new IConnection.Key(info.name, new IpfsKey(info.id));
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("gen", keyName, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("gen", keyName, e);
		}
	}


	private IpfsConnectionException _handleIpfsRuntimeException(String action, Object context, RuntimeException e) throws IpfsConnectionException, AssertionError
	{
		// For some reason, IPFS wraps java.net.SocketTimeoutException in RuntimeException, but we want to expose that here.
		try
		{
			throw e.getCause();
		}
		catch (SocketTimeoutException timeout)
		{
			throw new IpfsConnectionException(action, context, timeout);
		}
		catch (IOException ioe)
		{
			// If the IPFS node experiences something like an internal server error, we will see that as IOException here.
			// From our perspective, that is still just a network error.
			throw new IpfsConnectionException(action, context, ioe);
		}
		catch (Throwable t)
		{
			// Unknown.
			throw Assert.unexpected(t);
		}
	}
}
