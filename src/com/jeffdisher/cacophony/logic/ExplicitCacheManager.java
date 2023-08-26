package com.jeffdisher.cacophony.logic;

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
	private final Context _context;

	/**
	 * Creates the receiver on top of the given context, using it to access the network and explicit storage.
	 * 
	 * @param context The context.
	 */
	public ExplicitCacheManager(Context context)
	{
		_context = context;
	}

	/**
	 * Loads user info for the given public key.
	 * 
	 * @param publicKey The user to fetch.
	 * @return The future containing the asynchronous result.
	 */
	public FutureUserInfo loadUserInfo(IpfsKey publicKey)
	{
		FutureUserInfo future = new FutureUserInfo();
		try
		{
			ExplicitCacheData.UserInfo info = ExplicitCacheLogic.loadUserInfo(_context, publicKey);
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
		return future;
	}

	/**
	 * Loads the record info at the given cid.
	 * 
	 * @param cid The record instance to load.
	 * @return The future containing the asynchronous result.
	 */
	public FutureRecord loadRecord(IpfsFile cid)
	{
		FutureRecord future = new FutureRecord();
		try
		{
			CachedRecordInfo info = ExplicitCacheLogic.loadRecordInfo(_context, cid);
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
		return future;
	}

	/**
	 * Requests to that the explicit cache be fully purged and a storage GC of the IPFS node be initiated.
	 * 
	 * @return The future containing the asynchronous completion.
	 */
	public FutureVoid purgeCacheFullyAndGc()
	{
		FutureVoid future = new FutureVoid();
		try
		{
			ExplicitCacheLogic.purgeCacheFullyAndGc(_context);
			future.success();
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
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
		return ExplicitCacheLogic.getExistingRecordInfo(_context, cid);
	}

	/**
	 * @return The current explicit cache size, in bytes.
	 */
	public long getExplicitCacheSize()
	{
		return ExplicitCacheLogic.getExplicitCacheSize(_context);
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
