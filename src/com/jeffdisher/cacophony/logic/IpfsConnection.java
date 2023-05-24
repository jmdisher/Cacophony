package com.jeffdisher.cacophony.logic;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;


public class IpfsConnection implements IConnection
{
	private final Uploader _uploader;
	private final IPFS _defaultConnection;
	private final IPFS _longWaitConnection;

	/**
	 * Creates the IPFS connection abstraction.
	 * Note that the underlying connections appear to be stateless, and so is this object, so concurrent connections are
	 * safe and will function independently.
	 * 
	 * @param uploader The uploader to use for posting data streams to the IPFS.
	 * @param defaultConnection The connection to use for most calls.
	 * @param longWaitConnection The connection to use for slow calls (pin).
	 */
	public IpfsConnection(Uploader uploader, IPFS defaultConnection, IPFS longWaitConnection)
	{
		_uploader = uploader;
		_defaultConnection = defaultConnection;
		_longWaitConnection = longWaitConnection;
	}

	@Override
	public IpfsFile storeData(InputStream dataStream) throws IpfsConnectionException
	{
		try
		{
			return _uploader.uploadFileInline(_defaultConnection.host, _defaultConnection.port, dataStream);
		}
		catch (InterruptedException e)
		{
			// We don't use interruption on common threads.
			throw Assert.unexpected(e);
		}
		catch (TimeoutException e)
		{
			// We will interpret this as an IPFS exception (since a network timeout would seem appropriate).
			throw new IpfsConnectionException("store", "stream", e);
		}
		catch (ExecutionException e)
		{
			// We will interpret this as an IPFS exception since it isn't obvious why this happens (docs are poor).
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
	public void publish(String keyName, IpfsKey publicKey, IpfsFile file) throws IpfsConnectionException
	{
		// We expect that the caller already validated this name.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != file);
		
		IpfsConnectionException error = null;
		try
		{
			_initialPublish(keyName, file);
			error = null;
		}
		catch (IpfsConnectionException e)
		{
			error = e;
		}
		
		// We sometimes get an odd RuntimeException "IOException contacting IPFS daemon" so we will consider this a success if we can at least resolve the name to what we expected.
		if (null != error)
		{
			// If we never got a normal success from the publish, we will at least still claim to have succeeded if the key has been updated on the local node.
			try
			{
				IpfsFile published = _resolve(publicKey);
				if (published.equals(file))
				{
					// Even if there was a timeout error, the correct answer is there so it may have completed asynchronously.
					error = null;
				}
			}
			catch (IpfsConnectionException e)
			{
				// We will ignore this since we want the original exception.
			}
		}
		if (null != error)
		{
			throw error;
		}
	}

	@Override
	public IpfsFile resolve(IpfsKey key) throws IpfsConnectionException
	{
		Assert.assertTrue(null != key);
		return _resolve(key);
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
	public IpfsKey getOrCreatePublicKey(String keyName) throws IpfsConnectionException
	{
		// We expect that the caller already validated this name.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		String context = "lookup";
		try
		{
			List<IpfsKey> matchOrEmpty = _defaultConnection.key.list().stream()
					.filter((KeyInfo info) -> keyName.equals(info.name))
					.map((KeyInfo info) -> new IpfsKey(info.id))
					.collect(Collectors.toList())
			;
			// We can have only 1 or 0 entries in this list.
			Assert.assertTrue(matchOrEmpty.size() <= 1);
			IpfsKey publicKey = (1 == matchOrEmpty.size())
					? matchOrEmpty.get(0)
					: null
			;
			if (null == publicKey)
			{
				// We need to create the key.
				context = "creation";
				KeyInfo info = _defaultConnection.key.gen(keyName, Optional.empty(), Optional.empty());
				publicKey = new IpfsKey(info.id);
			}
			// Any failures should hit the exception cases.
			Assert.assertTrue(null != publicKey);
			return publicKey;
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("getOrCreatePublicKey", context, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("getOrCreatePublicKey", context, e);
		}
	}

	@Override
	public void deletePublicKey(String keyName) throws IpfsConnectionException
	{
		try
		{
			_defaultConnection.key.rm(keyName);
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("deletePublicKey", keyName, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("deletePublicKey", keyName, e);
		}
	}


	private IpfsConnectionException _handleIpfsRuntimeException(String action, Object context, RuntimeException e) throws IpfsConnectionException
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
			// This is typically what happens if the IPFS node is taking too long to respond (typically not being able to find a resource on the network).
			throw new IpfsConnectionException(action, context, timeout);
		}
		catch (IOException ioe)
		{
			// If the IPFS node experiences something like an internal server error, we will see that as IOException here.
			// From our perspective, that is still just a network error.
			throw new IpfsConnectionException(action, context, ioe);
		}
		catch (RuntimeException re)
		{
			// This usually means that the node isn't running:
			// java.lang.RuntimeException: Couldn't connect to IPFS daemon... Is IPFS running?
			throw new IpfsConnectionException(action, context, re);
		}
		catch (Throwable t)
		{
			// Unknown.
			throw Assert.unexpected(t);
		}
	}

	private IpfsFile _resolve(IpfsKey key) throws IpfsConnectionException, AssertionError
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

	private void _initialPublish(String keyName, IpfsFile file) throws IpfsConnectionException
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
}
