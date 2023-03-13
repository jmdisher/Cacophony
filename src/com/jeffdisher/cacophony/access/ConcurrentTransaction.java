package com.jeffdisher.cacophony.access;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
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

	public ConcurrentTransaction(INetworkScheduler scheduler, Set<IpfsFile> existingPin)
	{
		_scheduler = scheduler;
		_existingPin = existingPin;
		_changedPinCounts = new HashMap<>();
		_networkPins = new HashSet<>();
	}

	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _scheduler.getSizeInBytes(cid);
	}

	public <R> FutureRead<R> loadCached(IpfsFile cid, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(_existingPin.contains(cid) || (_changedPinCounts.get(cid) > 0));
		return _scheduler.readData(cid, decoder);
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
		// Commit back with updated counts.
		target.commitTransactionPinCanges(_changedPinCounts, Collections.emptySet());
	}

	public void rollback(IStateResolver target)
	{
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
}
