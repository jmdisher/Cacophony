package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.ILogger;
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
	private List<IpfsFile> _initialFailuresToRetry;
	private Set<IpfsFile> _initialFailureSet;
	private long _currentCacheUsageInBytes;
	private FutureResolve _keyResolve;

	private boolean _didRun;
	private IpfsFile _newRoot;
	private StandardRefreshSupport _refreshSupport;
	private boolean _isSuccess;
	private boolean _moreToDo;
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
		// previousRoot can be null.
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
			, FolloweeData followees
	) throws IpfsConnectionException
	{
		Assert.assertTrue(!_didSetup);
		
		// We only want to shrink the cache if this isn't a delete (in that case, we will just make sure it isn't overflowing).
		double fullnessFraction = ConcurrentFolloweeRefresher.NO_RESIZE_FOLLOWEE_FULLNESS_FRACTION;
		if (!_isDelete)
		{
			// If this followee hasn't been refreshed before, that means it was only just added and its data hasn't been
			// fetched, so we want to make extra room in the cache.
			boolean isFirstRefresh = (null == followees.getLastFetchedRootForFollowee(_followeeKey));
			fullnessFraction = isFirstRefresh
					? ConcurrentFolloweeRefresher.NEW_FOLLOWEE_FULLNESS_FRACTION
					: ConcurrentFolloweeRefresher.EXISTING_FOLLOWEE_FULLNESS_FRACTION
			;
		}
		CommandHelpers.shrinkCacheToFitInPrefs(_logger, access, fullnessFraction);
		_transaction = access.openConcurrentTransaction();
		_cachedEntriesForFollowee = (null != _previousRoot)
				? followees.snapshotAllElementsForFollowee(_followeeKey)
				: Collections.emptyMap()
		;
		// Get the list of temporary failures, in case we decide to retry any.
		_initialFailuresToRetry = (null != _previousRoot)
				? new ArrayList<>(followees.getSkippedRecords(_followeeKey, true))
				: Collections.emptyList()
		;
		
		// Get all the skipped records, not just the temporary ones.
		_initialFailureSet = (null != _previousRoot)
				? followees.getSkippedRecords(_followeeKey, false)
				: Collections.emptySet()
		;
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
	 * @param cacheUpdater Used for updating internal caches.
	 * @return True if the refresh was a success, false if an error prevented it from completing (finishRefresh must be
	 * called no matter the return value).
	 */
	public boolean runRefresh(CacheUpdater cacheUpdater)
	{
		Assert.assertTrue(_didSetup);
		Assert.assertTrue(!_didRun);
		
		// We only want to state that we are mid-refresh if we know of this followee, already.
		boolean isExistingFollowee = (null != _previousRoot);
		if (isExistingFollowee)
		{
			cacheUpdater.followeeRefreshInProgress(_followeeKey, true);
		}
		_refreshSupport = new StandardRefreshSupport(_logger
				, _transaction
				, _followeeKey
				, isExistingFollowee
				, _cachedEntriesForFollowee
				, _initialFailuresToRetry
				, _initialFailureSet
		);
		boolean refreshWasSuccess = false;
		ILogger log = _logger.logStart("Starting concurrent refresh: " + _followeeKey);
		try
		{
			if (_isDelete)
			{
				_moreToDo = FolloweeRefreshLogic.refreshFollowee(_refreshSupport
						, _prefs
						, _previousRoot
						, null
						, _currentCacheUsageInBytes
				);
				// There CANNOT be more to do if this was a delete.
				Assert.assertTrue(!_moreToDo);
				refreshWasSuccess = true;
			}
			else
			{
				_newRoot = _keyResolve.get();
				// If this failed to resolve, it will throw.
				Assert.assertTrue(null != _newRoot);
				_moreToDo = FolloweeRefreshLogic.refreshFollowee(_refreshSupport
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
			log.logOperation("Followee meta-data element too big (probably wrong file published):  " + e.getLocalizedMessage());
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
		_didRun = true;
		_isSuccess = refreshWasSuccess;
		return refreshWasSuccess;
	}

	/**
	 * Step 3:  Finishes the refresh operation, closing the concurrent transaction and writing back, or otherwise
	 * rationalizing, any cached state against the authoritative access object.
	 * 
	 * @param access System write access.
	 * @param cacheUpdater Used for updating internal caches.
	 * @param followees The followees structure to update.
	 * @param currentTimeMillis The current time of the refresh, in milliseconds since the epoch.
	 * @return True if there is more work which could be done with this followee (incomplete incremental sync).
	 * @throws IpfsConnectionException There was a problem accessing data from the network.
	 * @throws ProtocolDataException Found data which violated the constraints of the Cacophony protocol.
	 * @throws KeyException There was an error resolving the followee key (probably expired from IPNS).
	 */
	public boolean finishRefresh(IWritingAccess access
			, CacheUpdater cacheUpdater
			, FolloweeData followees
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
			else if (null == _previousRoot)
			{
				// Create the new record.
				followees.createNewFollowee(_followeeKey, _newRoot, currentTimeMillis, currentTimeMillis);
				_refreshSupport.commitFolloweeChanges(followees);
			}
			else
			{
				// Update existing record.
				_refreshSupport.commitFolloweeChanges(followees);
				followees.updateExistingFollowee(_followeeKey, _newRoot, currentTimeMillis, _isSuccess);
			}
			// The record cache is null in cases where this is a one-off operation and there is no cache.
			_refreshSupport.commitLocalCacheUpdates(cacheUpdater);
			_transaction.commit(resolver);
		}
		else
		{
			if (null != _previousRoot)
			{
				// In the failure case, we still want to update the followee, if we have it, so that we don't get stuck on a missing followee (usually not refreshed key).
				followees.updateExistingFollowee(_followeeKey, _previousRoot, currentTimeMillis, _isSuccess);
			}
			_transaction.rollback(resolver);
		}
		_didFinish = true;
		// We only want to state that the refresh completed if we know of this followee, already.
		if (null != _previousRoot)
		{
			cacheUpdater.followeeRefreshInProgress(_followeeKey, false);
		}
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
		// Return true if there is still an incremental sync starting-point since there is more work to do.
		return _moreToDo;
	}
}
