package com.jeffdisher.cacophony.logic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.IExplicitCacheReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * The logic associated with the "explicit cache" used by the system.  "Explicit", in this case means data which needed
 * to be cached because it was explicitly requested, not cached for reasons of availability and load balancing, like
 * followee-related caches.
 * It is structured as a least-recently-used read-through cache under the write lock provided by IWritingAccess.  It
 * tracks data within the local IPFS node which was pinned due to these explicit lookups.  This means that, despite
 * being highly versatile, it is very heavy-weight and has an on-disk representation.
 * Relationship with PinCache:  The explicit cache is used when building the PinCache since it adds references to pinned
 * data on the local node.
 * Relationship with LocalRecordCache and LocalUserInfoCache:  This exists as a peer to them, in the stack.  While they
 * represent fast-path look-ups for data known for structural reasons (home user data or followee data), this represents
 * a slower path for actual management of data cached for other reasons.  This means that users of the cache should
 * check those other caches first, as they are fast ephemeral projections.  Accessing this cache is potentially much
 * slower (read-through, so it may do network access, plus a read updates the LRU state meaning it always needs to
 * write-back to disk).
 * Since those other ephemeral caches should be preferentially used, there will be relatively little overlap between
 * this and those other caches.  An example where they may overlap is if this cache is used to view a post which results
 * in a decision to follow the user, thus putting the cached entry in the followee cache.  In that case, later calls to
 * find it will resolve it in those fast-path caches hitting before this cache is checked, allowing it to age out and be
 * purged.
 * Note that this implementation will internally request whatever kind of data locking access is required for what it
 * needs to accomplish and will avoid doing heavy network operations under lock, relying on transactions for those cases
 * where it would need to write-back storage due to changes it has made to what is pinned on the local node.
 * Due to the need for potential background refreshes and a general interest to batch requests in fewer disk-write
 * calls, the actual logic is run on a background thread (if created with enableAsync=true) and the results are returned
 * via futures (where the call cannot be satisfied with a fully-synchronous local call).
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
	 * Loads user info for the user with the given public key, reading through to the network to find the info if it
	 * isn't already in the cache.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * WARNING:  As part of a transition to the background refresh mechanism, this call will currently always report a
	 * cache hit if it sees an entry in the cache for this key, no matter how old it is.
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
	 * Loads the info describing the StreamRecord with the given recordCid.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * 
	 * @param recordCid The record instance to load.
	 * @return The future containing the asynchronous result.
	 */
	public synchronized FutureRecord loadRecord(IpfsFile recordCid)
	{
		FutureRecord future = new FutureRecord();
		if (null != _runnables)
		{
			_runnables.add(() -> _loadRecord(recordCid, future));
			this.notifyAll();
		}
		else
		{
			_loadRecord(recordCid, future);
		}
		return future;
	}

	/**
	 * Purges everything from the explicit cache and requests a GC of the IPFS node.
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
	 * Returns the record with the given recordCid, returning null if the explicit cache doesn't have information about
	 * it.
	 * NOTE:  Will NOT load from the network.
	 * 
	 * @param recordCid The record instance to read.
	 * @return The record, or null if not found.
	 */
	public CachedRecordInfo getExistingRecord(IpfsFile recordCid)
	{
		try (IReadingAccess access = StandardAccess.readAccessBasic(_accessTuple, _logger))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			return data.getRecordInfo(recordCid);
		}
	}

	/**
	 * Just a helper to read the total size from ExplicitCacheData.
	 * 
	 * @return The current explicit cache size, in bytes.
	 */
	public long getExplicitCacheSize()
	{
		try (IReadingAccess access = StandardAccess.readAccessBasic(_accessTuple, _logger))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			return data.getCacheSizeBytes();
		}
	}


	private void _loadUserInfo(IpfsKey publicKey, FutureUserInfo future)
	{
		long currentTimeMillis = _currentTimeMillisSupplier.getAsLong();
		try
		{
			ExplicitCacheData.UserInfo info;
			IpfsFile root = null;
			ConcurrentTransaction transaction = null;
			try (IReadingAccess access = StandardAccess.readAccessBasic(_accessTuple, _logger))
			{
				IExplicitCacheReading data = access.readableExplicitCache();
				info = data.getUserInfo(publicKey);
				if (null == info)
				{
					// Check the key.
					root = access.resolvePublicKey(publicKey).get();
					// This will fail instead of returning null.
					Assert.assertTrue(null != root);
					transaction = access.openConcurrentTransaction();
				}
			}
			
			if (null != transaction)
			{
				Assert.assertTrue(null == info);
				ExplicitCacheData.UserInfo potential;
				try
				{
					potential = _loadUserInfo(transaction, root);
				}
				catch (ProtocolDataException | IpfsConnectionException e)
				{
					try (IWritingAccess access = StandardAccess.writeAccessBasic(_accessTuple, _logger))
					{
						ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
						transaction.rollback(resolver);
					}
					throw e;
				}
				try (IWritingAccess access = StandardAccess.writeAccessBasic(_accessTuple, _logger))
				{
					ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
					ExplicitCacheData data = access.writableExplicitCache();
					info = data.getUserInfo(publicKey);
					if (null == info)
					{
						// Add this to the structure, creating the official result.
						info = data.addUserInfo(publicKey, currentTimeMillis, potential.indexCid(), potential.recommendationsCid(), potential.recordsCid(), potential.descriptionCid(), potential.userPicCid(), potential.combinedSizeBytes());
						
						// Commit the transaction.
						transaction.commit(resolver);
						
						// Purge any overflow.
						PrefsData prefs = access.readPrefs();
						_purgeExcess(access, data, prefs.explicitCacheTargetBytes);
					}
					else
					{
						// We will just use this one so rollback the transaction as its network operations will be redundant.
						transaction.rollback(resolver);
					}
				}
			}
			Assert.assertTrue(null != info);
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

	private void _loadRecord(IpfsFile recordCid, FutureRecord future)
	{
		try
		{
			CachedRecordInfo info;
			int videoEdgePixelMax = 0;
			ConcurrentTransaction transaction = null;
			try (IReadingAccess access = StandardAccess.readAccessBasic(_accessTuple, _logger))
			{
				IExplicitCacheReading data = access.readableExplicitCache();
				info = data.getRecordInfo(recordCid);
				if (null == info)
				{
					transaction = access.openConcurrentTransaction();
					videoEdgePixelMax = access.readPrefs().videoEdgePixelMax;
				}
			}
			
			if (null != transaction)
			{
				Assert.assertTrue(null == info);
				Assert.assertTrue(videoEdgePixelMax > 0);
				CachedRecordInfo potential;
				try
				{
					potential = _loadRecordInfo(transaction, videoEdgePixelMax, recordCid);
				}
				catch (ProtocolDataException | IpfsConnectionException e)
				{
					try (IWritingAccess access = StandardAccess.writeAccessBasic(_accessTuple, _logger))
					{
						ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
						transaction.rollback(resolver);
					}
					throw e;
				}
				try (IWritingAccess access = StandardAccess.writeAccessBasic(_accessTuple, _logger))
				{
					ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
					ExplicitCacheData data = access.writableExplicitCache();
					info = data.getRecordInfo(recordCid);
					if (null == info)
					{
						// Add this to the structure.
						data.addStreamRecord(recordCid, potential);
						info = potential;
						
						// Commit the transaction.
						transaction.commit(resolver);
						
						// Purge any overflow.
						PrefsData prefs = access.readPrefs();
						_purgeExcess(access, data, prefs.explicitCacheTargetBytes);
					}
					else
					{
						// We will just use this one so rollback the transaction as its network operations will be redundant.
						transaction.rollback(resolver);
					}
				}
			}
			Assert.assertTrue(null != info);
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
			try (IWritingAccess access = StandardAccess.writeAccessBasic(_accessTuple, _logger))
			{
				ExplicitCacheData data = access.writableExplicitCache();
				_purgeExcess(access, data, 0L);
				access.requestIpfsGc();
			}
			future.success();
		}
		catch (IpfsConnectionException e)
		{
			future.failure(e);
		}
	}

	private void _purgeExcess(IWritingAccess access, ExplicitCacheData data, long cacheLimitInBytes)
	{
		data.purgeCacheToSize((IpfsFile evict) -> {
			try
			{
				access.unpin(evict);
			}
			catch (IpfsConnectionException e)
			{
				// This is just a local contact problem so just log it.
				System.err.println("WARNING:  Failure in unpin, will need to be removed manually: " + evict);
			}
		}, cacheLimitInBytes);
	}

	private ExplicitCacheData.UserInfo _loadUserInfo(ConcurrentTransaction transaction, IpfsFile root) throws ProtocolDataException, IpfsConnectionException
	{
		// First, read all of the data to make sure that it is valid.
		ForeignChannelReader reader = new ForeignChannelReader(transaction, root, false);
		AbstractIndex index = reader.loadIndex();
		AbstractDescription description = reader.loadDescription();
		// (recommendations is something we don't use but will pin later so we want to know it is valid)
		reader.loadRecommendations();
		// We need to check the user pic, explicitly.
		IpfsFile userPicCid = description.getPicCid();
		// In V2, the user pic is optional.
		long picSize = (null != userPicCid)
				? transaction.getSizeInBytes(userPicCid).get()
				: 0L
		;
		if (picSize > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
		{
			throw new SizeConstraintException("explicit user pic", picSize, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
		}
		// Now, pin everything and update the cache.
		FuturePin pinIndex = transaction.pin(root);
		FuturePin pinRecommendations = transaction.pin(index.recommendationsCid);
		FuturePin pinRecords = transaction.pin(index.recordsCid);
		FuturePin pinDescription = transaction.pin(index.descriptionCid);
		if (null != userPicCid)
		{
			transaction.pin(userPicCid).get();
		}
		pinIndex.get();
		pinRecommendations.get();
		pinRecords.get();
		pinDescription.get();
		long combinedSizeBytes = transaction.getSizeInBytes(pinIndex.cid).get()
				+ transaction.getSizeInBytes(pinRecommendations.cid).get()
				+ transaction.getSizeInBytes(pinRecords.cid).get()
				+ transaction.getSizeInBytes(pinDescription.cid).get()
				+ picSize
		;
		// We don't bother building this fully since it is just meant to be a container of a few pieces of data, here.
		return new ExplicitCacheData.UserInfo(null
				, 0L
				, 0L
				, pinIndex.cid
				, pinRecommendations.cid
				, pinRecords.cid
				, pinDescription.cid
				, userPicCid
				, combinedSizeBytes
		);
	}

	private CachedRecordInfo _loadRecordInfo(ConcurrentTransaction transaction, int videoEdgePixelMax, IpfsFile recordCid) throws ProtocolDataException, IpfsConnectionException
	{
		CachedRecordInfo info = CommonRecordPinning.loadAndPinRecord(transaction, videoEdgePixelMax, recordCid);
		// This is never null - throws on error.
		Assert.assertTrue(null != info);
		return info;
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