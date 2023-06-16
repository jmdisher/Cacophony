package com.jeffdisher.cacophony.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.IObservableFuture;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This class exists to provide minimal access to network and pin state without holding the writing access lock.
 * The reason why that is valuable is that some long-running operations have logic intertwined with long-running network
 * operations (specifically, refreshing followees).
 * Using an instance of this class allows for a simple and well-defined way to perform these operations without blocking
 * any other operations.  Hence, this allows operations to run concurrently with other activities on the system.
 * The general flow is:
 * -acquire read access lock to request the transaction, then release the lock
 * -perform long-running concurrent operation against the transaction
 * -acquire the write access lock to commit/rollback the transaction, then release that lock
 * 
 * WARNING:  It is possible that another operation using the write access lock could unpin a resource we are assuming to
 * be pinned, here.  We rely on the IPFS node note aggressively GC-ing unpinned resources in order to keep this from
 * causing problems.
 * TODO:  Develop a mechanism for in-flight transactions to block unpinning in the write access, forcing them to
 * complete only after all transactions have completed.
 */
public class ConcurrentTransaction
{
	private final INetworkScheduler _scheduler;
	private final Set<IpfsFile> _existingPin;
	private final Map<IpfsFile, Integer> _changedPinCounts;
	private final Set<IpfsFile> _networkPins;

	// We track any asynchronous operations we started so that we can verify that they were all observed, on commit or rollback.
	private final List<IObservableFuture> _futures;

	public ConcurrentTransaction(INetworkScheduler scheduler, Set<IpfsFile> existingPin)
	{
		_scheduler = scheduler;
		_existingPin = existingPin;
		_changedPinCounts = new HashMap<>();
		_networkPins = new HashSet<>();
		_futures = new ArrayList<>();
	}

	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize result = _scheduler.getSizeInBytes(cid);
		_futures.add(result);
		return result;
	}

	public <R> FutureRead<R> loadCached(IpfsFile cid, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(_existingPin.contains(cid) || (_changedPinCounts.get(cid) > 0));
		FutureRead<R> result = _scheduler.readData(cid, decoder);
		_futures.add(result);
		return result;
	}

	public FuturePin pin(IpfsFile cid)
	{
		int modifiedCount = _changedPinCounts.containsKey(cid)
				? _changedPinCounts.get(cid)
				: 0
		;
		FuturePin result = null;
		if (_existingPin.contains(cid) || (modifiedCount > 0))
		{
			// If this is something pinned before the transaction started, or during the transaction, just fake the operation.
			result = new FuturePin(cid);
			result.success();
		}
		else
		{
			// We don't think anyone has pinned this yet, so pin it on the network.
			result = _scheduler.pin(cid);
			_futures.add(result);
			// We record this so that we can tell the write access to undo it on rollback.
			boolean isNew = _networkPins.add(cid);
			Assert.assertTrue(isNew);
		}
		// Record that we changed the reference count on this resource.
		_changedPinCounts.put(cid, (modifiedCount + 1));
		return result;
	}

	public void unpin(IpfsFile cid)
	{
		int modifiedCount = _changedPinCounts.containsKey(cid)
				? _changedPinCounts.get(cid)
				: 0
		;
		// We can't unpin until we commit but we can record that we wanted to decrement the reference count.
		_changedPinCounts.put(cid, (modifiedCount - 1));
	}

	public void commit(IStateResolver target)
	{
		// Make sure that the caller has consumed everything.
		for (IObservableFuture observable : _futures)
		{
			Assert.assertTrue(observable.wasObserved());
		}
		// Commit back with updated counts.
		target.commitTransactionPinCanges(_changedPinCounts, Collections.emptySet());
	}

	public void rollback(IStateResolver target)
	{
		// The futures may not have been observed but we still want them to complete in case, for example, the rollback
		// needs to unpin something the transaction decided to pin which hasn't completed yet (could cause a leak on the
		// IPFS node).
		for (IObservableFuture observable : _futures)
		{
			observable.waitForCompletion();
		}
		// Rollback with an empty map - this way, we will change nothing but allow the access to know we are done (if they were tracking us).
		target.commitTransactionPinCanges(Collections.emptyMap(), _networkPins);
	}


	/**
	 * This should actually just call through to IWritingAccess.  It is spelled out directly just to make testing eaiser.
	 */
	public static interface IStateResolver
	{
		void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins);
	}


	/**
	 * Non-testing cases all use the same state resolver so this helper is used to create it.
	 * 
	 * @param access The writing access which will be the target of the commit.
	 * @return The resolver which can be used for commit or rollback.
	 */
	public static IStateResolver buildCommonResolver(IWritingAccess access)
	{
		return (Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins) ->
		{
			access.commitTransactionPinCanges(changedPinCounts, falsePins);
		};
	}
}
