package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.IExplicitCacheReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;
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
	private final LocalDataModel _sharedDataModel;
	private final IConnection _basicConnection;
	private final INetworkScheduler _scheduler;
	private final ILogger _logger;
	private final LongSupplier _currentTimeMillisSupplier;
	private final Thread _background;
	// We keep the pending requests for user data and record data in lists so that we can batch them.
	// These are stored as maps since we don't want duplicated requests and this allows new calls to piggy-back.
	private final Map<IpfsKey, FutureUserInfo> _userRequests;
	private final Map<IpfsFile, FutureRecord> _recordRequests;
	// We only keep at most a single purge request.
	private FutureVoid _purgeRequest;

	private boolean _isBackgroundRunning;

	/**
	 * Creates the manager on top of the given components..
	 * 
	 * @param sharedDataModel The shared LocalDataModel instance.
	 * @param basicConnection The low-level IPFS connection.
	 * @param scheduler The network scheduler.
	 * @param logger The logger.
	 * @param currentTimeMillisSupplier A supplier of the current system time in milliseconds.
	 * @param enableAsync True if the manager should run in a truly asynchronous mode.
	 */
	public ExplicitCacheManager(LocalDataModel sharedDataModel, IConnection basicConnection, INetworkScheduler scheduler, ILogger logger, LongSupplier currentTimeMillisSupplier, boolean enableAsync)
	{
		_sharedDataModel = sharedDataModel;
		_basicConnection = basicConnection;
		_scheduler = scheduler;
		_logger = logger;
		_currentTimeMillisSupplier = currentTimeMillisSupplier;
		if (enableAsync)
		{
			_background = MiscHelpers.createThread(() -> {
				Runnable runner = _backgroundGetNextRunnable();
				while (null != runner)
				{
					runner.run();
					runner = _backgroundGetNextRunnable();
				}
			}, "ExplicitCacheManager");
			_userRequests = new HashMap<>();
			_recordRequests = new HashMap<>();
			_isBackgroundRunning = true;
			_background.start();
		}
		else
		{
			_background = null;
			_userRequests = null;
			_recordRequests = null;
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
		FutureUserInfo future;
		if (null != _background)
		{
			future = _userRequests.get(publicKey);
			if (null == future)
			{
				future = new FutureUserInfo(publicKey);
				_userRequests.put(publicKey, future);
				this.notifyAll();
				
			}
		}
		else
		{
			future = new FutureUserInfo(publicKey);
			FutureUserInfo[] usersToLoad = new FutureUserInfo[] { future };
			_runFullLoad(usersToLoad, new FutureRecord[0], null);
		}
		return future;
	}

	/**
	 * Loads the info describing the StreamRecord with the given recordCid.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * If the data is present in the cache, but requestLeaves is true and there are missing leaves, the leaves will be
	 * fetched.
	 * If the data is missing and requestLeaves is false, only the meta-data will be cached.
	 * 
	 * @param recordCid The record instance to load.
	 * @param requestLeaves If true, will only return once the leaves are populated, as well.
	 * @return The future containing the asynchronous result.
	 */
	public synchronized FutureRecord loadRecord(IpfsFile recordCid, boolean requestLeaves)
	{
		FutureRecord future;
		if (null != _background)
		{
			future = _recordRequests.get(recordCid);
			// If the request is here and we want leaves but the future isn't requesting them, we will replace it with a new instance.
			FutureRecord flowThrough = null;
			if ((null != future) && requestLeaves && !future.requestLeaves)
			{
				flowThrough = future;
				future = null;
			}
			if (null == future)
			{
				future = new FutureRecord(recordCid, requestLeaves, flowThrough);
				_recordRequests.put(recordCid, future);
				this.notifyAll();
			}
		}
		else
		{
			future = new FutureRecord(recordCid, requestLeaves, null);
			FutureRecord[] recordsToLoad = new FutureRecord[] { future };
			_runFullLoad(new FutureUserInfo[0], recordsToLoad, null);
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
		FutureVoid future;
		if (null != _background)
		{
			future = _purgeRequest;
			if (null == future)
			{
				future = new FutureVoid();
				_purgeRequest = future;
				this.notifyAll();
			}
		}
		else
		{
			future = new FutureVoid();
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
		try (IReadingAccess access = StandardAccess.readAccess(_basicConnection, _scheduler, _logger, _sharedDataModel, null))
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
		try (IReadingAccess access = StandardAccess.readAccess(_basicConnection, _scheduler, _logger, _sharedDataModel, null))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			return data.getCacheSizeBytes();
		}
	}


	private void _purgeCacheFullyAndGc(FutureVoid future)
	{
		try
		{
			try (IWritingAccess access = StandardAccess.writeAccess(_basicConnection, _scheduler, _logger, _sharedDataModel, null))
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
		// (same thing with records)
		reader.loadRecords();
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

	private CachedRecordInfo _loadRecordInfo(ConcurrentTransaction transaction, int videoEdgePixelMax, IpfsFile recordCid, boolean shouldPinLeaves) throws ProtocolDataException, IpfsConnectionException
	{
		CachedRecordInfo info = CommonRecordPinning.loadAndPinRecord(transaction, videoEdgePixelMax, recordCid, shouldPinLeaves);
		// This is never null - throws on error.
		Assert.assertTrue(null != info);
		return info;
	}

	private synchronized Runnable _backgroundGetNextRunnable()
	{
		// Wait for something to do.
		while (_isBackgroundRunning
				&& _userRequests.isEmpty()
				&& _recordRequests.isEmpty()
				&& (null == _purgeRequest)
		)
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
		Runnable toDo = null;
		if (_isBackgroundRunning)
		{
			// Drain the pending work and pass them into a runnable to perform the next batch.
			FutureUserInfo[] usersToLoad = _userRequests.values().stream()
					.collect(Collectors.toList())
					.toArray((int size) -> new FutureUserInfo[size])
			;
			_userRequests.clear();
			FutureRecord[] recordsToLoad = _recordRequests.values().stream()
					.collect(Collectors.toList())
					.toArray((int size) -> new FutureRecord[size])
			;
			_recordRequests.clear();
			FutureVoid purgeRequest = _purgeRequest;
			_purgeRequest = null;
			toDo = () -> {
				_runFullLoad(usersToLoad, recordsToLoad, purgeRequest);
			};
		}
		return toDo;
	}

	private void _runFullLoad(FutureUserInfo[] usersToLoad, FutureRecord[] recordsToLoad, FutureVoid purgeRequest)
	{
		// We will just assume that the entire refresh happens at the same time.
		long currentTimeMillis = _currentTimeMillisSupplier.getAsLong();
		
		// We will null out these parameters as we satisfy each individual request since they may be satisfied at different points.
		
		// Step 1:
		// -check existing caches
		// -read prefs info we need (since those could change between invocations)
		// -starting the fetch of any remaining keys
		// -open a transaction for anything else we need to do
		int videoEdgePixelMax;
		long explicitCacheTargetBytes;
		ConcurrentTransaction[] userTransactions = new ConcurrentTransaction[usersToLoad.length];
		ConcurrentTransaction[] recordTransactions = new ConcurrentTransaction[recordsToLoad.length];
		FutureResolve[] keys = new FutureResolve[usersToLoad.length];
		boolean[] recordsBeingReplaced = new boolean[recordsToLoad.length];
		List<UserRefreshTuple> userInfoRefreshes = new ArrayList<>();
		try (IReadingAccess access = StandardAccess.readAccess(_basicConnection, _scheduler, _logger, _sharedDataModel, null))
		{
			PrefsData prefs = access.readPrefs();
			videoEdgePixelMax = prefs.videoEdgePixelMax;
			explicitCacheTargetBytes = prefs.explicitCacheTargetBytes;
			long explicitUserInfoRefreshMillis = prefs.explicitUserInfoRefreshMillis;
			
			IExplicitCacheReading data = access.readableExplicitCache();
			for (int i = 0; i < usersToLoad.length; ++i)
			{
				FutureUserInfo user = usersToLoad[i];
				ExplicitCacheData.UserInfo info = data.getUserInfo(user.publicKey);
				if (null != info)
				{
					user.success(info);
					usersToLoad[i] = null;
					// Note that we will still refresh this if the entry is stale.
					if ((info.lastFetchAttemptMillis() + explicitUserInfoRefreshMillis) < currentTimeMillis)
					{
						// This is expired so add it to the refresh list, even though we already returned the "hit".
						FutureResolve resolve = access.resolvePublicKey(user.publicKey);
						ConcurrentTransaction refreshTransaction = access.openConcurrentTransaction();
						UserRefreshTuple tuple = new UserRefreshTuple();
						tuple.key = user.publicKey;
						tuple.oldInfo = info;
						tuple.resolve = resolve;
						tuple.commitTransaction = refreshTransaction;
						userInfoRefreshes.add(tuple);
					}
				}
				else
				{
					keys[i] = access.resolvePublicKey(user.publicKey);
					userTransactions[i] = access.openConcurrentTransaction();
				}
			}
			for (int i = 0; i < recordsToLoad.length; ++i)
			{
				FutureRecord record = recordsToLoad[i];
				CachedRecordInfo info = data.getRecordInfo(record.recordCid);
				// Make sure that this has all the data which was requested.
				if (record.requestLeaves && (null != info) && info.hasDataToCache())
				{
					// We want all the data but the data is incomplete so null out this cache hit.
					info = null;
					recordsBeingReplaced[i] = true;
				}
				if (null != info)
				{
					record.success(info);
					recordsToLoad[i] = null;
				}
				else
				{
					recordTransactions[i] = access.openConcurrentTransaction();
				}
			}
		}
		
		// Step 2:
		// -wait for any remaining keys to be resolved
		List<ConcurrentTransaction> transactionsToCommit = new ArrayList<>();
		List<ConcurrentTransaction> transactionsToRollback = new ArrayList<>();
		IpfsFile[] rootsToLoad = new IpfsFile[keys.length];
		for (int i = 0; i < rootsToLoad.length; ++i)
		{
			FutureResolve key = keys[i];
			if (null != key)
			{
				try
				{
					rootsToLoad[i] = key.get();
				}
				catch (KeyException e)
				{
					usersToLoad[i].keyException(e);
					usersToLoad[i] = null;
					transactionsToRollback.add(userTransactions[i]);
					userTransactions[i] = null;
				}
			}
		}
		// We will also start any user info refreshes here.
		for (UserRefreshTuple tuple : userInfoRefreshes)
		{
			try
			{
				tuple.resolvedRoot = tuple.resolve.get();
			}
			catch (KeyException e)
			{
				tuple.rollbackTransaction = tuple.commitTransaction;
				tuple.commitTransaction = null;
			}
		}
		
		// Step 3:
		// -perform any other look-ups with the transaction
		ExplicitCacheData.UserInfo[] usersToWriteBack = new ExplicitCacheData.UserInfo[usersToLoad.length];
		for (int i = 0; i < usersToLoad.length; ++i)
		{
			FutureUserInfo user = usersToLoad[i];
			if (null != user)
			{
				try
				{
					ExplicitCacheData.UserInfo info = _loadUserInfo(userTransactions[i], rootsToLoad[i]);
					// This will throw instead of failing.
					Assert.assertTrue(null != info);
					usersToWriteBack[i] = info;
				}
				catch (ProtocolDataException e)
				{
					user.dataException(e);
					usersToLoad[i] = null;
					transactionsToRollback.add(userTransactions[i]);
					userTransactions[i] = null;
				}
				catch (IpfsConnectionException e)
				{
					user.connectionException(e);
					usersToLoad[i] = null;
					transactionsToRollback.add(userTransactions[i]);
					userTransactions[i] = null;
				}
			}
		}
		CachedRecordInfo[] recordsToWriteBack = new CachedRecordInfo[recordsToLoad.length];
		for (int i = 0; i < recordsToLoad.length; ++i)
		{
			FutureRecord record = recordsToLoad[i];
			if (null != record)
			{
				try
				{
					CachedRecordInfo info = _loadRecordInfo(recordTransactions[i], videoEdgePixelMax, record.recordCid, record.requestLeaves);
					// This will throw instead of failing.
					Assert.assertTrue(null != info);
					recordsToWriteBack[i] = info;
				}
				catch (ProtocolDataException e)
				{
					record.dataException(e);
					recordsToLoad[i] = null;
					transactionsToRollback.add(recordTransactions[i]);
					recordTransactions[i] = null;
				}
				catch (IpfsConnectionException e)
				{
					record.connectionException(e);
					recordsToLoad[i] = null;
					transactionsToRollback.add(recordTransactions[i]);
					recordTransactions[i] = null;
				}
			}
		}
		for (UserRefreshTuple tuple : userInfoRefreshes)
		{
			if (null != tuple.commitTransaction)
			{
				// Before loading the data associated with the user, see if it has changed.
				if (tuple.resolvedRoot.equals(tuple.oldInfo.indexCid()))
				{
					// The root we resolved is unchanged so just use the existing info.
					tuple.newInfo = tuple.oldInfo;
				}
				else
				{
					// This is a new root so fetch the data.
					try
					{
						tuple.newInfo = _loadUserInfo(tuple.commitTransaction, tuple.resolvedRoot);
						// This will throw instead of failing.
						Assert.assertTrue(null != tuple.newInfo);
						// We also need to update this by removing the old info (these might overlap but the transaction should do the counting).
						tuple.commitTransaction.unpin(tuple.oldInfo.indexCid());
						tuple.commitTransaction.unpin(tuple.oldInfo.recommendationsCid());
						tuple.commitTransaction.unpin(tuple.oldInfo.recordsCid());
						tuple.commitTransaction.unpin(tuple.oldInfo.descriptionCid());
						IpfsFile pic = tuple.oldInfo.userPicCid();
						if (null != pic)
						{
							tuple.commitTransaction.unpin(pic);
						}
					}
					catch (ProtocolDataException | IpfsConnectionException e)
					{
						tuple.rollbackTransaction = tuple.commitTransaction;
						tuple.commitTransaction = null;
					}
				}
			}
		}
		
		// Step 4:
		// -commit the transaction
		// -update the cache data
		// -run any purge request
		try (IWritingAccess access = StandardAccess.writeAccess(_basicConnection, _scheduler, _logger, _sharedDataModel, null))
		{
			ExplicitCacheData data = access.writableExplicitCache();
			for (int i = 0; i < usersToLoad.length; ++i)
			{
				FutureUserInfo user = usersToLoad[i];
				if (null != user)
				{
					// Verify that nothing has changed in the cache - we are the only ones changing it so this is just to prove that.
					Assert.assertTrue(null == data.getUserInfo(user.publicKey));
					ExplicitCacheData.UserInfo info = usersToWriteBack[i];
					data.addUserInfo(user.publicKey
							, currentTimeMillis
							, info.indexCid()
							, info.recommendationsCid()
							, info.recordsCid()
							, info.descriptionCid()
							, info.userPicCid()
							, info.combinedSizeBytes()
					);
					
					user.success(info);
					usersToLoad[i] = null;
					transactionsToCommit.add(userTransactions[i]);
					userTransactions[i] = null;
				}
			}
			for (int i = 0; i < recordsToLoad.length; ++i)
			{
				FutureRecord record = recordsToLoad[i];
				if (null != record)
				{
					CachedRecordInfo info = recordsToWriteBack[i];
					// See if this is new or a replacement (this check is just to verify correctness).
					if (recordsBeingReplaced[i])
					{
						data.replaceStreamRecord(record.recordCid, info);
					}
					else
					{
						// Verify that nothing has changed in the cache - we are the only ones changing it so this is just to prove that.
						Assert.assertTrue(null == data.getRecordInfo(record.recordCid));
						data.addStreamRecord(record.recordCid, info);
					}
					
					record.success(info);
					recordsToLoad[i] = null;
					transactionsToCommit.add(recordTransactions[i]);
					recordTransactions[i] = null;
				}
			}
			for (UserRefreshTuple tuple : userInfoRefreshes)
			{
				if (null != tuple.commitTransaction)
				{
					data.successRefreshUserInfo(tuple.key
							, currentTimeMillis
							, tuple.newInfo.indexCid()
							, tuple.newInfo.recommendationsCid()
							, tuple.newInfo.recordsCid()
							, tuple.newInfo.descriptionCid()
							, tuple.newInfo.userPicCid()
							, tuple.newInfo.combinedSizeBytes()
					);
					transactionsToCommit.add(tuple.commitTransaction);
					tuple.commitTransaction = null;
				}
				else
				{
					Assert.assertTrue(null != tuple.rollbackTransaction);
					data.failedRefreshUserInfo(tuple.key, currentTimeMillis);
					transactionsToRollback.add(tuple.rollbackTransaction);
					tuple.rollbackTransaction = null;
				}
			}
			
			ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
			for (ConcurrentTransaction commit : transactionsToCommit)
			{
				commit.commit(resolver);
			}
			for (ConcurrentTransaction rollback : transactionsToRollback)
			{
				rollback.rollback(resolver);
			}
			
			if (null != purgeRequest)
			{
				// If we were given an explicit purge request, it actually means we need to clear the entire cache and complete the future.
				_purgeExcess(access, data, 0L);
				try
				{
					access.requestIpfsGc();
					purgeRequest.success();
				}
				catch (IpfsConnectionException e)
				{
					purgeRequest.failure(e);
				}
				purgeRequest = null;
			}
			else
			{
				// If not, we just want to the basic purge of anything over limit.
				_purgeExcess(access, data, explicitCacheTargetBytes);
			}
		}
		
		// Verify that everything is done from the lists.
		for (FutureUserInfo user : usersToLoad)
		{
			Assert.assertTrue(null == user);
		}
		for (FutureRecord record : recordsToLoad)
		{
			Assert.assertTrue(null == record);
		}
		for (ConcurrentTransaction transaction : userTransactions)
		{
			Assert.assertTrue(null == transaction);
		}
		for (ConcurrentTransaction transaction : recordTransactions)
		{
			Assert.assertTrue(null == transaction);
		}
		for (UserRefreshTuple tuple : userInfoRefreshes)
		{
			Assert.assertTrue(null == tuple.commitTransaction);
			Assert.assertTrue(null == tuple.rollbackTransaction);
		}
		Assert.assertTrue(null == purgeRequest);
	}


	/**
	 * The asynchronous result of a user info load.
	 */
	public static class FutureUserInfo
	{
		public final IpfsKey publicKey;
		private ExplicitCacheData.UserInfo _info;
		private KeyException _keyException;
		private ProtocolDataException _protocolException;
		private IpfsConnectionException _connectionException;
		
		public FutureUserInfo(IpfsKey publicKey)
		{
			this.publicKey = publicKey;
		}
		
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
		public final IpfsFile recordCid;
		public final boolean requestLeaves;
		// If we "upgraded" the request, we want to also keep the old copy (since someone is waiting on it) to notify on completion.
		private final FutureRecord _flowThrough;
		private CachedRecordInfo _info;
		private ProtocolDataException _protocolException;
		private IpfsConnectionException _connectionException;
		
		public FutureRecord(IpfsFile recordCid, boolean requestLeaves, FutureRecord flowThrough)
		{
			this.recordCid = recordCid;
			this.requestLeaves = requestLeaves;
			_flowThrough = flowThrough;
		}
		
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
			
			if (null != _flowThrough)
			{
				_flowThrough.success(info);
			}
		}
		
		public synchronized void dataException(ProtocolDataException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_protocolException = e;
			this.notifyAll();
			
			if (null != _flowThrough)
			{
				_flowThrough.dataException(e);
			}
		}
		
		public synchronized void connectionException(IpfsConnectionException e)
		{
			Assert.assertTrue(null != e);
			Assert.assertTrue(null == _info);
			Assert.assertTrue(null == _protocolException);
			Assert.assertTrue(null == _connectionException);
			_connectionException = e;
			this.notifyAll();
			
			if (null != _flowThrough)
			{
				_flowThrough.connectionException(e);
			}
		}
	}


	private static class UserRefreshTuple
	{
		public IpfsKey key;
		public ExplicitCacheData.UserInfo oldInfo;
		public FutureResolve resolve;
		public IpfsFile resolvedRoot;
		public ExplicitCacheData.UserInfo newInfo;
		public ConcurrentTransaction commitTransaction;
		public ConcurrentTransaction rollbackTransaction;
	}
}
