package com.jeffdisher.cacophony.logic;

import java.util.Map;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Instances of this object are used to store the intermediate state and data associated with a followee refresh
 * operation (including start/stop following).
 * It explicitly tracks the state it is in so usage errors will result in assertion failure.
 */
public class ConcurrentFolloweeRefresher
{
	public static final double NEW_FOLLOWEE_FULLNESS_FRACTION = 0.75;
	public static final double EXISTING_FOLLOWEE_FULLNESS_FRACTION = 0.90;
	public static final double NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION = 1.0;

	private final ILogger _logger;
	private final IpfsKey _followeeKey;
	private final IpfsFile _previousRoot;
	private final PrefsData _prefs;
	private final boolean _isDelete;

	private boolean _didSetup;
	private ConcurrentTransaction _transaction;
	private Map<IpfsFile, FollowingCacheElement> _cachedEntriesForFollowee;
	private long _currentCacheUsageInBytes;
	private FutureResolve _keyResolve;

	private boolean _didRun;
	private IpfsFile _newRoot;
	private StandardRefreshSupport _refreshSupport;
	private boolean _isSuccess;
	private IpfsConnectionException _connectionException;
	private ProtocolDataException _protocolException;
	private KeyException _keyException;

	private boolean _didFinish;

	/**
	 * Creates a new refresher, in the initial state.
	 * 
	 * @param environment The environment to use for logging.
	 * @param followeeKey The public key of the followee we want to refresh.
	 * @param previousRoot The root (StreamIndex) CID from the last refresh (null if this is a start).
	 * @param prefs The preferences object.
	 * @param isDelete True if we should be deleting this followee (removing all data instead of updating).
	 */
	public ConcurrentFolloweeRefresher(ILogger logger
			, IpfsKey followeeKey
			, IpfsFile previousRoot
			, PrefsData prefs
			, boolean isDelete
	)
	{
		Assert.assertTrue(null != logger);
		Assert.assertTrue(null != followeeKey);
		Assert.assertTrue(null != previousRoot);
		Assert.assertTrue(null != prefs);
		
		_logger = logger;
		_followeeKey = followeeKey;
		_previousRoot = previousRoot;
		_prefs = prefs;
		_isDelete = isDelete;
	}

	/**
	 * Step 1:  Setup the state of the object for the refresh while still holding write access.  This step is also
	 * responsible for the pre-refresh cache cleaning.
	 * 
	 * @param access System write access.
	 * @param followees The followees structure.
	 * @throws IpfsConnectionException There was a failure unpinning during the cache cleaning operation.
	 */
	public void setupRefresh(IWritingAccess access
			, IFolloweeWriting followees
	) throws IpfsConnectionException
	{
		Assert.assertTrue(!_didSetup);
		
		// We only want to shrink the cache if this isn't a delete (in that case, we will just make sure it isn't overflowing).
		double fullnessFraction = ConcurrentFolloweeRefresher.NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION;
		if (!_isDelete)
		{
			// If this followee hasn't been refreshed before, that means it was only just added and its data hasn't been
			// fetched, so we want to make extra room in the cache.
			boolean isFirstRefresh = (0L == followees.getLastPollMillisForFollowee(_followeeKey));
			fullnessFraction = isFirstRefresh
					? ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION
					: ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION
			;
		}
		CommandHelpers.shrinkCacheToFitInPrefs(_logger, access, fullnessFraction);
		_transaction = access.openConcurrentTransaction();
		_cachedEntriesForFollowee = followees.snapshotAllElementsForFollowee(_followeeKey);
		Assert.assertTrue(null != _cachedEntriesForFollowee);
		_currentCacheUsageInBytes = CacheHelpers.getCurrentCacheSizeBytes(followees);
		_keyResolve = _isDelete
				? null
				: access.resolvePublicKey(_followeeKey)
		;
		_didSetup = true;
	}

	/**
	 * Step 2:  Run the actual refresh of the followee.  Note that this happens without holding the access token, as it
	 * runs on its internal state snapshot from the setup and using a concurrent transaction created at that time.
	 * 
	 * @param entryRegistry The registry of connectors for communicating records reachable from a followee's key.
	 * @return True if the refresh was a success, false if an error prevented it from completing (finishRefresh must be
	 * called no matter the return value).
	 */
	public boolean runRefresh(EntryCacheRegistry entryRegistry)
	{
		Assert.assertTrue(_didSetup);
		Assert.assertTrue(!_didRun);
		
		if (null != entryRegistry)
		{
			entryRegistry.setSpecial(_followeeKey, "Refreshing");
		}
		_refreshSupport = new StandardRefreshSupport(_logger
				, _transaction
				, _followeeKey
				, _cachedEntriesForFollowee
				, entryRegistry
		);
		boolean refreshWasSuccess = false;
		ILogger log = _logger.logStart("Starting concurrent refresh: " + _followeeKey);
		try
		{
			if (_isDelete)
			{
				FolloweeRefreshLogic.refreshFollowee(_refreshSupport
						, _prefs
						, _previousRoot
						, null
						, _currentCacheUsageInBytes
				);
				refreshWasSuccess = true;
			}
			else
			{
				_newRoot = _keyResolve.get();
				// If this failed to resolve, it will throw.
				Assert.assertTrue(null != _newRoot);
				FolloweeRefreshLogic.refreshFollowee(_refreshSupport
						, _prefs
						, _previousRoot
						, _newRoot
						, _currentCacheUsageInBytes
				);
				refreshWasSuccess = true;
			}
		}
		catch (IpfsConnectionException e)
		{
			log.logOperation("Network failure in refresh: " + e.getLocalizedMessage());
			_connectionException = e;
			refreshWasSuccess = false;
		}
		catch (KeyException e)
		{
			log.logOperation("Key resolution failure in refresh: " + e.getLocalizedMessage());
			_keyException = e;
			refreshWasSuccess = false;
		}
		catch (SizeConstraintException e)
		{
			log.logOperation("Root index element too big (probably wrong file published): " + e.getLocalizedMessage());
			_protocolException = e;
			refreshWasSuccess = false;
		}
		catch (FailedDeserializationException e)
		{
			log.logOperation("Followee data appears to be corrupt: " + e.getLocalizedMessage());
			_protocolException = e;
			refreshWasSuccess = false;
		}
		finally
		{
			if (refreshWasSuccess)
			{
				log.logFinish("Refresh success");
			}
			else
			{
				log.logFinish("Refresh aborted and will be retried in the future");
			}
		}
		if (null != entryRegistry)
		{
			entryRegistry.setSpecial(_followeeKey, null);
		}
		_didRun = true;
		_isSuccess = refreshWasSuccess;
		return refreshWasSuccess;
	}

	/**
	 * Step 3:  Finishes the refresh operation, closing the concurrent transaction and writing back, or otherwise
	 * rationalizing, any cached state against the authoritative access object.
	 * 
	 * @param access System write access.
	 * @param recordCache The local cache which should be updated in response to finishing this refresh (can be null).
	 * @param userInfoCache The user info cache which should be updated in response to finishing this refresh (can be null).
	 * @param replyCache The cache of the replyTo relationships to update when adding/removing followee posts with replies.
	 * @param followees The followees structure to update.
	 * @param currentTimeMillis The current time of the refresh, in milliseconds since the epoch.
	 * @throws IpfsConnectionException There was a problem accessing data from the network.
	 * @throws ProtocolDataException Found data which violated the constraints of the Cacophony protocol.
	 * @throws KeyException There was an error resolving the followee key (probably expired from IPNS).
	 */
	public void finishRefresh(IWritingAccess access
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, HomeUserReplyCache replyCache
			, IFolloweeWriting followees
			, long currentTimeMillis
	) throws IpfsConnectionException, ProtocolDataException, KeyException
	{
		Assert.assertTrue(_didRun);
		Assert.assertTrue(!_didFinish);
		
		ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
		if (_isSuccess)
		{
			if (_isDelete)
			{
				// Delete the record.
				_refreshSupport.commitFolloweeChanges(followees);
				followees.removeFollowee(_followeeKey);
			}
			else
			{
				// Update existing record.
				_refreshSupport.commitFolloweeChanges(followees);
				followees.updateExistingFollowee(_followeeKey, _newRoot, currentTimeMillis);
			}
			// The record cache is null in cases where this is a one-off operation and there is no cache.
			if (null != recordCache)
			{
				_refreshSupport.commitLocalCacheUpdates(recordCache, userInfoCache, replyCache);
			}
			_transaction.commit(resolver);
		}
		else
		{
			// In the failure case, we still want to update the followee, if we have it, so that we don't get stuck on a missing followee (usually not refreshed key).
			followees.updateExistingFollowee(_followeeKey, _previousRoot, currentTimeMillis);
			_transaction.rollback(resolver);
		}
		_didFinish = true;
		// At this point, we want to throw any exceptions we caught in the main operation.
		if (null != _connectionException)
		{
			throw _connectionException;
		}
		else if (null != _protocolException)
		{
			throw _protocolException;
		}
		else if (null != _keyException)
		{
			throw _keyException;
		}
	}
}
