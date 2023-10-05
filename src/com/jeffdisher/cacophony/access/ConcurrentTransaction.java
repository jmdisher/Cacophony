package com.jeffdisher.cacophony.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.IObservableFuture;
import com.jeffdisher.cacophony.types.DataDeserializer;
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
 * be pinned, here.  We rely on the IPFS node not aggressively GC-ing unpinned resources in order to keep this from
 * causing problems.  This could be addressed by coordinating the commits of multiple in-flight transactions in order to
 * make sure that the pins are forced to happen before unpins, so this couldn't happen, but that would add significant
 * complexity and some non-obvious blocking behaviour for a case which shouldn't happen in normal usage (and can
 * technically be recovered in the common case - the pin will likely be satisfied by the network).
 */
public class ConcurrentTransaction implements IBasicNetworkOps
{
	private final INetworkScheduler _scheduler;
	private final Set<IpfsFile> _existingPin;
	private final Map<IpfsFile, Integer> _changedPinCounts;
	private final Set<IpfsFile> _networkPins;

	// We track any asynchronous operations we started so that we can verify that they were all observed, on commit or rollback.
	private final List<IObservableFuture> _futures;

	/**
	 * Creates the transaction on top of the given scheduler, assuming the existing set of pinned files given.
	 * 
	 * @param scheduler The scheduler to use for network operations.
	 * @param existingPin The existing set of files we can assume are pinned at the start of the transaction.
	 */
	public ConcurrentTransaction(INetworkScheduler scheduler, Set<IpfsFile> existingPin)
	{
		_scheduler = scheduler;
		_existingPin = existingPin;
		_changedPinCounts = new HashMap<>();
		_networkPins = new HashSet<>();
		_futures = new ArrayList<>();
	}

	/**
	 * Reads the size of a given CID, on the network.
	 * 
	 * @param cid The CID to read.
	 * @return The size of the given CID, in bytes, as a future.
	 */
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		FutureSize result = _scheduler.getSizeInBytes(cid);
		_futures.add(result);
		return result;
	}

	@Override
	public <R> FutureRead<R> loadCached(IpfsFile cid, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(_existingPin.contains(cid) || (_changedPinCounts.get(cid) > 0));
		FutureRead<R> result = _scheduler.readData(cid, decoder);
		_futures.add(result);
		return result;
	}

	@Override
	public <R> FutureSizedRead<R> loadNotCached(IpfsFile cid, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
	{
		FutureSizedRead<R> result = _scheduler.readDataWithSizeCheck(cid, context, maxSizeInBytes, decoder);
		_futures.add(result);
		return result;
	}

	/**
	 * Pins a given file on the local node.
	 * 
	 * @param cid The CID to pin.
	 * @return The result of the pin, as a future.
	 */
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

	/**
	 * Records that the given CID should be unpinned on the local node, when the transaction commits.
	 * 
	 * @param cid The CID to unpin.
	 */
	public void unpin(IpfsFile cid)
	{
		int modifiedCount = _changedPinCounts.containsKey(cid)
				? _changedPinCounts.get(cid)
				: 0
		;
		// We can't unpin until we commit but we can record that we wanted to decrement the reference count.
		_changedPinCounts.put(cid, (modifiedCount - 1));
	}

	/**
	 * Commits the transaction, verifying that all associated network operations have been observed and then writes
	 * back the final pin changes to the given resolver target.
	 * 
	 * @param target The resolver to use for updating pin counts.
	 */
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

	/**
	 * Rolls-back and fails out of the transaction, waiting for all operations to complete (although they may not all
	 * have been observed), and instructs the given resolver target to reverse any pin operations performed.
	 * 
	 * @param target The resolve to use for reverting pin changes.
	 */
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
	 * This should actually just call through to IWritingAccess.  It is spelled out directly just to make testing easier.
	 */
	public static interface IStateResolver
	{
		/**
		 * Called when the transaction completes, either via commit or rollback.  The implementation is given the pin
		 * state changes to instruct it how to rationalize the changes to the local IPFS node with its shared
		 * representation.
		 * 
		 * @param changedPinCounts The pin counts which have changed, in commit.
		 * @param falsePins The pins which should be reverted, on rollback.
		 */
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
