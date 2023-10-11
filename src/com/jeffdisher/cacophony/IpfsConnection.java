package com.jeffdisher.cacophony;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.logic.Uploader;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;


/**
 * An implementation of the connection used when contacting a real IPFS API server.
 */
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
	public Map<String, IpfsKey> getLocalPublicKeys() throws IpfsConnectionException
	{
		String context = "lookup";
		try
		{
			Map<String, IpfsKey> allKeys = _defaultConnection.key.list().stream()
					.collect(Collectors.toMap(
							(KeyInfo info) -> info.name,
							(KeyInfo info) -> new IpfsKey(info.id)
					))
			;
			// Any failures should hit the exception cases.
			Assert.assertTrue(null != allKeys);
			return allKeys;
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
	public IpfsKey generateLocalPublicKey(String keyName) throws IpfsConnectionException
	{
		// We expect that the caller already validated this name.
		Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		String context = "creation";
		try
		{
			KeyInfo info = _defaultConnection.key.gen(keyName, Optional.empty(), Optional.empty());
			IpfsKey publicKey = new IpfsKey(info.id);
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

	@Override
	public String getIpfsStatus() throws IpfsConnectionException
	{
		try
		{
			return _defaultConnection.diag.sys();
		}
		catch (RuntimeException e)
		{
			throw _handleIpfsRuntimeException("getIpfsStatus", null, e);
		}
		catch (IOException e)
		{
			throw new IpfsConnectionException("getIpfsStatus", null, e);
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

	private IpfsFile _resolve(IpfsKey key) throws IpfsConnectionException
	{
		// The key must exist.
		Assert.assertTrue(null != key);
		
		// We can't use the IPFS library for this since we need to specify "nocache=true" or else we tend to get stale data, as of Kubo 0.20.0.
		String fullUrl = "http://" + _defaultConnection.host + ":" + _defaultConnection.port + "/api/v0/name/resolve"
				+ "?arg=" + key.toPublicKey()
				+ "&nocache=true"
		;
		try
		{
			HttpURLConnection connection = (HttpURLConnection) new URL(fullUrl).openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			// We use the same timeouts as _defaultConnection:  https://github.com/ipfs-shipyard/java-ipfs-http-client/blob/master/src/main/java/io/ipfs/api/IPFS.java
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(60_000);
			connection.setDoOutput(true);
			byte[] rawData = connection.getInputStream().readAllBytes();
			// Parse the data as JSON and get the "Path" key to extract the IPFS path.
			JsonObject object = Json.parse(new String(rawData, StandardCharsets.UTF_8)).asObject();
			String publishedPath = object.getString("Path", null);
			String published = publishedPath.substring(publishedPath.lastIndexOf("/") + 1);
			return IpfsFile.fromIpfsCid(published);
		}
		catch (MalformedURLException e)
		{
			// This would be a static error.
			throw Assert.unexpected(e);
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
