package com.jeffdisher.cacophony.logic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Currently just a wrapper over the helpers in ExplicitCacheLogic but this will eventually absorb those
 * responsibilities as it transforms into a background explicit cache manager able to schedule refreshes of data and
 * fetch new data with less lock-step blocking that the current mechanisms require.
 */
public class ExplicitCacheManager
{
	private final Context.AccessTuple _accessTuple;
	private final ILogger _logger;
	private final LongSupplier _currentTimeMillisSupplier;
	private final Thread _background;
	// We will use a runnables directly in this list for now, but this will change later to allow better batching.
	private final Queue<Runnable> _runnables;

	private boolean _isBackgroundRunning;

	/**
	 * Creates the receiver on top of the given context, using it to access the network and explicit storage.
	 * 
	 * @param context The context.
	 * @param enableAsync True if the manager should run in a truly asynchronous mode.
	 */
	public ExplicitCacheManager(Context.AccessTuple accessTuple, ILogger logger, LongSupplier currentTimeMillisSupplier, boolean enableAsync)
	{
		_accessTuple = accessTuple;
		_logger = logger;
		_currentTimeMillisSupplier = currentTimeMillisSupplier;
		if (enableAsync)
		{
			_background = new Thread(() -> {
				Runnable runner = _backgroundGetNextRunnable();
				while (null != runner)
				{
					runner.run();
					runner = _backgroundGetNextRunnable();
				}
			});
			_runnables = new LinkedList<>();
			_isBackgroundRunning = true;
			_background.start();
		}
		else
		{
			_background = null;
			_runnables = null;
			_isBackgroundRunning = false;
		}
	}

	/**
	 * Shuts down the background processing associated with async mode.  Note that this is REQUIRED when running in
	 * asynchronous mode but is always good practice.
	 * For testing brevity, synchronous tests don't always call this.
	 */
	public void shutdown()
	{
		if (_isBackgroundRunning)
		{
			synchronized (this)
			{
				_isBackgroundRunning = false;
				this.notifyAll();
			}
			try
			{
				_background.join();
			}
			catch (InterruptedException e)
			{
				throw Assert.unexpected(e);
			}
		}
	}

	/**
	 * Loads user info for the given public key.
	 * 
	 * @param publicKey The user to fetch.
	 * @return The future containing the asynchronous result.
	 */
	public synchronized FutureUserInfo loadUserInfo(IpfsKey publicKey)
	{
		FutureUserInfo future = new FutureUserInfo();
		if (null != _runnables)
		{
			_runnables.add(() -> _loadUserInfo(publicKey, future));
			this.notifyAll();
		}
		else
		{
			_loadUserInfo(publicKey, future);
		}
		return future;
	}

	/**
	 * Loads the record info at the given cid.
	 * 
	 * @param cid The record instance to load.
	 * @return The future containing the asynchronous result.
	 */
	public synchronized FutureRecord loadRecord(IpfsFile cid)
	{
		FutureRecord future = new FutureRecord();
		if (null != _runnables)
		{
			_runnables.add(() -> _loadRecord(cid, future));
			this.notifyAll();
		}
		else
		{
			_loadRecord(cid, future);
		}
		return future;
	}

	/**
	 * Requests to that the explicit cache be fully purged and a storage GC of the IPFS node be initiated.
	 * 
	 * @return The future containing the asynchronous completion.
	 */
	public synchronized FutureVoid purgeCacheFullyAndGc()
	{
		FutureVoid future = new FutureVoid();
		
		if (null != _runnables)
		{
			_runnables.add(() -> _purgeCacheFullyAndGc(future));
			this.notifyAll();
		}
		else
		{
			_purgeCacheFullyAndGc(future);
		}
		return future;
	}

	/**
	 * Fetches a record directly from cache, returning immediately if found or not.
	 * 
	 * @param cid The record instance to read.
	 * @return The record, or null if not found.
	 */
	public CachedRecordInfo getExistingRecord(IpfsFile cid)
	{
		return ExplicitCacheLogic.getExistingRecordInfo(_accessTuple, _logger, cid);
	}

	/**
	 * @return The current explicit cache size, in bytes.
	 */
	public long getExplicitCacheSize()
	{
		return ExplicitCacheLogic.getExplicitCacheSize(_accessTuple, _logger);
	}


	private void _loadUserInfo(IpfsKey publicKey, FutureUserInfo future)
	{
		try
		{
			ExplicitCacheData.UserInfo info = ExplicitCacheLogic.loadUserInfo(_accessTuple, _logger, publicKey, _currentTimeMillisSupplier.getAsLong());
			future.success(info);
		}
		catch (KeyException e)
		{
			future.keyException(e);
		}
		catch (ProtocolDataException e)
		{
			future.dataException(e);
		}
		catch (IpfsConnectionException e)
		{
			future.connectionException(e);
		}
	}

	private void _loadRecord(IpfsFile cid, FutureRecord future)
	{
		try
		{
			CachedRecordInfo info = ExplicitCacheLogic.loadRecordInfo(_accessTuple, _logger, cid);
			future.success(info);
		}
		catch (ProtocolDataException e)
		{
			future.dataException(e);
		}
		catch (IpfsConnectionException e)
		{
			future.connectionException(e);
		}
	}

	private void _purgeCacheFullyAndGc(FutureVoid future)
	{
		try
		{
			ExplicitCacheLogic.purgeCacheFullyAndGc(_accessTuple, _logger);
			future.success();
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
		}
	}

	private synchronized Runnable _backgroundGetNextRunnable()
	{
		while (_isBackgroundRunning && _runnables.isEmpty())
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				throw Assert.unexpected(e);
			}
		}
		return _isBackgroundRunning
				? _runnables.remove()
				: null
		;
	}


	/**
	 * The asynchronous result of a user info load.
	 */
	public static class FutureUserInfo
	{
		private ExplicitCacheData.UserInfo _info;
		private KeyException _keyException;
		private ProtocolDataException _protocolException;
		private IpfsConnectionException _connectionException;
		
		public synchronized ExplicitCacheData.UserInfo get() throws KeyException, ProtocolDataException, IpfsConnectionException
		{
			while ((null == _info) && (null == _keyException) && (null == _protocolException) && (null == _connectionException))
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					// We don't use interruption in this system.
					throw Assert.unexpected(e);
				}
			}
			if (null != _keyException)
			{
				throw _keyException;
			}
			if (null != _protocolException)
			{
				throw _protocolException;
			}
			if (null != _connectionException)
			{
				throw _connectionException;
			}
			Assert.assertTrue(null != _info);
			return _info;
		}
		
		public synchronized void success(ExplicitCacheData.UserInfo info)
		{
			Assert.assertTrue(null != info);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _keyException);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_info = info;
			this.notifyAll();
		}
		
		public synchronized void keyException(KeyException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _keyException);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_keyException = e;
			this.notifyAll();
		}
		
		public synchronized void dataException(ProtocolDataException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _keyException);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_protocolException = e;
			this.notifyAll();
		}
		
		public synchronized void connectionException(IpfsConnectionException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _keyException);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_connectionException = e;
			this.notifyAll();
		}
	}


	/**
	 * The asynchronous result of a record load.
	 */
	public static class FutureRecord
	{
		private CachedRecordInfo _info;
		private ProtocolDataException _protocolException;
		private IpfsConnectionException _connectionException;
		
		public synchronized CachedRecordInfo get() throws ProtocolDataException, IpfsConnectionException
		{
			while ((null == _info) && (null == _protocolException) && (null == _connectionException))
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					// We don't use interruption in this system.
					throw Assert.unexpected(e);
				}
			}
			if (null != _protocolException)
			{
				throw _protocolException;
			}
			if (null != _connectionException)
			{
				throw _connectionException;
			}
			Assert.assertTrue(null != _info);
			return _info;
		}
		
		public synchronized void success(CachedRecordInfo info)
		{
			Assert.assertTrue(null != info);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_info = info;
			this.notifyAll();
		}
		
		public synchronized void dataException(ProtocolDataException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_protocolException = e;
			this.notifyAll();
		}
		
		public synchronized void connectionException(IpfsConnectionException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_connectionException = e;
			this.notifyAll();
		}
	}


	public static class FutureLong
	{
		private boolean _done;
		private long _result;
		
		public synchronized long get()
		{
			while (!_done)
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					// We don't use interruption in this system.
					throw Assert.unexpected(e);
				}
			}
			return _result;
		}
		
		public synchronized void success(long result)
		{
			Assert.assertTrue(!_done);
			_result = result;
			_done = true;
			this.notifyAll();
		}
	}
}
