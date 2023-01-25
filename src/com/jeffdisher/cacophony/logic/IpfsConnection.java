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
	private final IPFS _defaultConnection;
	private final IPFS _longWaitConnection;
	private final int _gatewayPort;

	/**
	 * Creates the IPFS connection abstraction.
	 * Note that the underlying connections appear to be stateless, and so is this object, so concurrent connections are
	 * safe and will function independently.
	 * 
	 * @param defaultConnection The connection to use for most calls.
	 * @param longWaitConnection The connection to use for slow calls (pin).
	 * @param gatewayPort The port to use when building URLs to directly fetch a file from the IPFS daemon.
	 */
	public IpfsConnection(IPFS defaultConnection, IPFS longWaitConnection, int gatewayPort)
	{
		_defaultConnection = defaultConnection;
		_longWaitConnection = longWaitConnection;
		_gatewayPort = gatewayPort;
	}

	@Override
	public List<Key> getKeys() throws IpfsConnectionException
	{
		try
		{
			return _defaultConnection.key.list().stream().map((info) -> new IConnection.Key(info.name, new IpfsKey(info.id))).collect(Collectors.toList());
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
			
			List<MerkleNode> nodes = _defaultConnection.add(wrapper);
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
			return _defaultConnection.cat(file.getMultihash());
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
			Map<?,?> map = _defaultConnection.name.publish(file.getMultihash(), Optional.of(keyName));
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
		Assert.assertTrue(null != key);
		try
		{
			String publishedPath = _defaultConnection.name.resolve(key.getMultihash());
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
			Map<?,?> dataMap = _defaultConnection.file.ls(cid.getMultihash());
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
		return _urlForDirectFetch(cid.toSafeString());
	}

	@Override
	public void pin(IpfsFile cid) throws IpfsConnectionException
	{
		try
		{
			// Pin will cause the daemon to pull the entire file locally before it returns so we need to use the long wait.
			_longWaitConnection.pin.add(cid.getMultihash());
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
			_defaultConnection.pin.rm(cid.getMultihash());
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
			KeyInfo info = _defaultConnection.key.gen(keyName, Optional.empty(), Optional.empty());
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

	@Override
	public void requestStorageGc() throws IpfsConnectionException
	{
		try
		{
			_defaultConnection.repo.gc();
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("gc", null, e);
		}
	}

	@Override
	public String directFetchUrlRoot()
	{
		return _urlForDirectFetch("").toString();
	}


	private IpfsConnectionException _handleIpfsRuntimeException(String action, Object context, RuntimeException e) throws IpfsConnectionException, AssertionError
	{
		// For some reason, IPFS wraps java.net.SocketTimeoutException in RuntimeException, but we want to expose that here.
		try
		{
			// Note that sometimes this cause is null so just throw it in that case.
			Throwable cause = e.getCause();
			if (null != cause)
			{
				throw cause;
			}
			else
			{
				throw e;
			}
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

	private URL _urlForDirectFetch(String fileName)
	{
		try
		{
			return new URL(_defaultConnection.protocol, _defaultConnection.host, _gatewayPort, "/ipfs/" + fileName);
		}
		catch (MalformedURLException e)
		{
			throw Assert.unexpected(e);
		}
	}
}
