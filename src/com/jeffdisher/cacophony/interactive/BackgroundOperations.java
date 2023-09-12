package com.jeffdisher.cacophony.interactive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * This will probably change substantially, over time, but it is intended to be used to track and/or manage background
 * operations being performed when running in interactive mode.
 * As a consequence for how this class handles long-running operations, it is essentially responsible for "owning" the
 * mutative "commands", as we would know them from the non-interactive mode.
 * We currently only report the start/stop of operations which have actually started since we don't preemptively
 * schedule them, but wait until we are ready to run them.
 */
public class BackgroundOperations
{
	// Instance variables which are never written after construction (safe everywhere).
	private final HandoffConnector<Integer, String> _connector;
	private final Thread _background;

	// Instance variables which are shared between caller thread and background thread (can only be touched on monitor).
	private boolean _handoff_keepRunning;
	private final PriorityQueue<SchedulableFollowee> _handoff_knownFollowees;
	private final Map<IpfsKey, SchedulableRepublish> _handoff_localChannelsByKey;
	private IpfsKey _handoff_currentlyPublishing;
	private long _republishIntervalMillis;
	private long _followeeRefreshMillis;

	// Instance variables which are only used by the background thread (only safe for the background thread).
	private int _background_nextOperationNumber;

	public BackgroundOperations(LongSupplier currentTimeMillisGenerator, ILogger logger, IOperationRunner operations, HandoffConnector<Integer, String> connector, long republishIntervalMillis, long followeeRefreshMillis)
	{
		Assert.assertTrue(republishIntervalMillis > 0L);
		Assert.assertTrue(followeeRefreshMillis > 0L);
		
		_connector = connector;
		_background = MiscHelpers.createThread(() -> {
			RequestedOperation operation = _background_consumeNextOperation(currentTimeMillisGenerator.getAsLong(), null);
			while (null != operation)
			{
				// If we have a publish operation, start that first, since that typically takes a long time.
				ILogger publishLog = null;
				FuturePublish publish = null;
				if (null != operation.publishTarget)
				{
					publishLog = logger.logStart("Start background publish for \"" + operation.publisherKeyName + "\" (" + operation.publisherKey + "): " + operation.publishTarget);
					publish = operations.startPublish(operation.publisherKeyName, operation.publisherKey, operation.publishTarget);
					Assert.assertTrue(null != publish);
					_connector.create(operation.publishNumber, "Publish " + operation.publishTarget);
				}
				// If we have a followee to refresh, do that work.
				// We also want to handle the case of pending incremental work a bit differently, since we will prioritize its scheduling.
				IpfsKey priorizedFolloweeRefresh = null;
				if (null != operation.followeeKey)
				{
					ILogger log = logger.logStart("Background start refresh: " + operation.followeeKey);
					_connector.create(operation.followeeNumber, "Refresh " + operation.followeeKey);
					OperationResult refreshResult = operations.refreshFollowee(operation.followeeKey);
					_connector.destroy(operation.followeeNumber);
					log.logFinish("Background end refresh: " + operation.followeeKey + " -> " + refreshResult);
					if (OperationResult.MORE_TO_DO == refreshResult)
					{
						priorizedFolloweeRefresh = operation.followeeKey;
					}
				}
				// Now, we can wait for the publish before we go back for more work.
				if (null != publish)
				{
					IpfsConnectionException error = publish.get();
					_connector.destroy(operation.publishNumber);
					if (null == error)
					{
						publishLog.logFinish("SUCCESS of background publish for \"" + operation.publisherKeyName + "\" (" + operation.publisherKey + "): " + operation.publishTarget);
					}
					else
					{
						publishLog.logFinish("FAILURE of background publish for \"" + operation.publisherKeyName + "\" (" + operation.publisherKey + "): " + error.getLocalizedMessage());
					}
				}
				operation = _background_consumeNextOperation(currentTimeMillisGenerator.getAsLong(), priorizedFolloweeRefresh);
			}
		}, "Background Operations");
		_republishIntervalMillis = republishIntervalMillis;
		_followeeRefreshMillis = followeeRefreshMillis;
		
		_background_nextOperationNumber = 1;
		
		_handoff_knownFollowees = new PriorityQueue<>(1, new Comparator<>() {
			@Override
			public int compare(SchedulableFollowee arg0, SchedulableFollowee arg1)
			{
				// The documentation of Comparator does a terrible job of saying how the order is actually derived from the result here so "if positive -> arg0 comes AFTER arg1").
				// In our case, we want the list to be sorted with the oldest refresh first.  Hence:  Ascending order on the last refresh millis.
				return Long.signum(arg0.nextRefreshMillis - arg1.nextRefreshMillis);
			}
		});
		_handoff_localChannelsByKey = new HashMap<>();
	}

	public void startProcess()
	{
		_handoff_keepRunning = true;
		_background.start();
	}

	public void shutdownProcess()
	{
		synchronized (this)
		{
			_handoff_keepRunning = false;
			this.notifyAll();
		}
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption on the top-level threads.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Adds a new channel to be periodically republished, in the background.  This is synchronized since the local
	 * channel map in a hand-off point.
	 * 
	 * @param keyName The name used for this key on the IPFS node.
	 * @param publicKey The actual public key.
	 * @param rootElement The initial StreamIndex CID.
	 */
	public synchronized void addChannel(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		// The same channel should never be redundantly added.
		Assert.assertTrue(!_handoff_localChannelsByKey.containsKey(publicKey));
		// We will treat our startup as though we have never published before, since this isn't worth tracking in the data store.
		long nextPublishMillis = 0L;
		SchedulableRepublish localChannel = new SchedulableRepublish(keyName, publicKey, rootElement, nextPublishMillis);
		_handoff_localChannelsByKey.put(publicKey, localChannel);
		this.notifyAll();
	}

	/**
	 * Removes an existing channel from those republished, in the background.  This is synchronized since the local
	 * channel map in a hand-off point.
	 * 
	 * @param publicKey The actual public key.
	 */
	public synchronized void removeChannel(IpfsKey publicKey)
	{
		// We expect the channel to be here.
		Assert.assertTrue(_handoff_localChannelsByKey.containsKey(publicKey));
		// We can just remove this.  Any pending operations will complete but not re-add since the re-schedule is done when selected.
		_handoff_localChannelsByKey.remove(publicKey);
	}

	public synchronized void requestPublish(IpfsKey publicKey, IpfsFile rootElement)
	{
		// A publish root must always be provided.
		Assert.assertTrue(null != rootElement);
		// We must already have this registered.
		Assert.assertTrue(_handoff_localChannelsByKey.containsKey(publicKey));
		
		// Just reset the publication time and add the new root.
		long nextPublishMillis = 0L;
		SchedulableRepublish original = _handoff_localChannelsByKey.remove(publicKey);
		SchedulableRepublish updated = new SchedulableRepublish(original.keyName, publicKey, rootElement, nextPublishMillis);
		_handoff_localChannelsByKey.put(publicKey, updated);
		this.notifyAll();
	}

	public void enqueueFolloweeRefresh(IpfsKey followeeKey, long lastRefreshMillis)
	{
		Assert.assertTrue(null != followeeKey);
		
		synchronized (this)
		{
			// We just add this to the priority list of followees and the background will decide what to do with it.
			long nextRefreshMillis = lastRefreshMillis + _followeeRefreshMillis;
			_handoff_knownFollowees.add(new SchedulableFollowee(followeeKey, nextRefreshMillis));
			this.notifyAll();
		}
	}

	public synchronized void intervalsWereUpdated(long republishIntervalMillis, long followeeRefreshMillis)
	{
		Assert.assertTrue(republishIntervalMillis > 0L);
		Assert.assertTrue(followeeRefreshMillis > 0L);
		
		// We schedule the events based on when we want them to happen (so we have flexibility in scheduling) so we will
		// need to update their scheduling and notify when done (will have no effect if nothing is ready).
		List<SchedulableRepublish> channelsToFix = new ArrayList<>(_handoff_localChannelsByKey.values());
		_handoff_localChannelsByKey.clear();
		for (SchedulableRepublish channel : channelsToFix)
		{
			// Subtract the old interval and add the new one.
			long nextRepublishMillis = channel.nextPublishMillis - _republishIntervalMillis + republishIntervalMillis;
			// If there is underflow, just say that this should be "now".
			if (nextRepublishMillis < 0L)
			{
				nextRepublishMillis = 0L;
			}
			_handoff_localChannelsByKey.put(channel.publicKey, new SchedulableRepublish(channel.keyName, channel.publicKey, channel.rootElement, nextRepublishMillis));
		}
		
		List<SchedulableFollowee> toFix = new ArrayList<>(_handoff_knownFollowees);
		_handoff_knownFollowees.clear();
		for (SchedulableFollowee followee : toFix)
		{
			// Subtract the old interval and add the new one.
			long nextRefreshMillis = followee.nextRefreshMillis - _followeeRefreshMillis + followeeRefreshMillis;
			// If there is underflow, just say that this should be "now".
			if (nextRefreshMillis < 0L)
			{
				nextRefreshMillis = 0L;
			}
			_handoff_knownFollowees.add(new SchedulableFollowee(followee.followee, nextRefreshMillis));
		}
		
		// Now, update the stored values for future scheduling decisions.
		_republishIntervalMillis = republishIntervalMillis;
		_followeeRefreshMillis = followeeRefreshMillis;
		
		this.notifyAll();
	}

	public synchronized boolean refreshFollowee(IpfsKey followeeKey)
	{
		boolean didFindFollowee = _locked_prioritizeFollowee(followeeKey, 0L);
		if (didFindFollowee)
		{
			this.notifyAll();
		}
		return didFindFollowee;
	}

	/**
	 * Removed the given followeeKey from internal tracking and scheduling.  The followee will not be refreshed after
	 * this call returns although a refresh could be in-progress if it started before this.
	 * 
	 * @param followeeKey The key to remove.
	 * @return True if the followee was found and removed from internal tracking.
	 */
	public synchronized boolean removeFollowee(IpfsKey followeeKey)
	{
		Assert.assertTrue(null != followeeKey);
		
		boolean didFindFollowee = false;
		Iterator<SchedulableFollowee> iter = _handoff_knownFollowees.iterator();
		while (iter.hasNext())
		{
			SchedulableFollowee elt = iter.next();
			if (elt.followee.equals(followeeKey))
			{
				// Remove this since we will re-add it.
				iter.remove();
				didFindFollowee = true;
				break;
			}
		}
		// Note that we don't bother with notification since this won't make anything schedule earlier.
		return didFindFollowee;
	}


	// Requests work to perform but also is responsible for the core scheduling operations - creating operations from the system described to us and the requests passed in from callers.
	private synchronized RequestedOperation _background_consumeNextOperation(long currentTimeMillis, IpfsKey priorizedFolloweeRefresh)
	{
		RequestedOperation work = null;
		if (_handoff_keepRunning)
		{
			boolean shouldNotify = false;
			// If we are coming back to find new work, the previous work must be done, so clear it and notify anyone waiting.
			if (null != _handoff_currentlyPublishing)
			{
				_handoff_currentlyPublishing = null;
				shouldNotify = true;
			}
			
			// Determine if we have publish work to do.
			String publishKeyName = null;
			IpfsKey publisherKey = null;
			IpfsFile publishRoot = null;
			long nextDueRepublishMillis = Long.MAX_VALUE;
			// We just walk the map since it is very rare that it has more than ~1 element.
			// For the same reason, we don't care which one we get - if multiple are ready, we will get around to them.
			for (SchedulableRepublish thisPublish : _handoff_localChannelsByKey.values())
			{
				// Before waiting, we want to see if we should perform any scheduler operations and then look at what kind of delay we should use.
				long dueTimePublishMillis = thisPublish.nextPublishMillis;
				if (dueTimePublishMillis <= currentTimeMillis)
				{
					publishKeyName = thisPublish.keyName;
					publisherKey = thisPublish.publicKey;
					publishRoot = thisPublish.rootElement;
					break;
				}
				else if (dueTimePublishMillis < nextDueRepublishMillis)
				{
					// If we don't find anyone, see when the next one is due.
					nextDueRepublishMillis = dueTimePublishMillis;
				}
			}
			int publishNumber = -1;
			if (null != publisherKey)
			{
				// The republish is due - set the current time as when we updated.
				SchedulableRepublish original = _handoff_localChannelsByKey.remove(publisherKey);
				long nextRepublishMillis = currentTimeMillis + _republishIntervalMillis;
				SchedulableRepublish updated = new SchedulableRepublish(publishKeyName, publisherKey, original.rootElement, nextRepublishMillis);
				_handoff_localChannelsByKey.put(publisherKey, updated);
				_handoff_currentlyPublishing = publisherKey;
				shouldNotify = true;
				publishNumber = _background_nextOperationNumber;
				_background_nextOperationNumber += 1;
			}
			
			// If we want to prioritize a followee, at this point, we will re-enqueue it.
			if (null != priorizedFolloweeRefresh)
			{
				// We want to say that it is due "now", meaning that anything behind schedule will run first but we can still run this immediately.
				_locked_prioritizeFollowee(priorizedFolloweeRefresh, currentTimeMillis);
			}
			
			// Determine if we have refresh work to do.
			IpfsKey refresh = null;
			int refreshNumber = -1;
			if (!_handoff_knownFollowees.isEmpty())
			{
				long dueTimeRefreshMillis = _handoff_knownFollowees.peek().nextRefreshMillis;
				if (dueTimeRefreshMillis <= currentTimeMillis)
				{
					// The refresh is due - remove this from the list and re-add a new schedulable at the end, with the current time.
					SchedulableFollowee followee = _handoff_knownFollowees.remove();
					long nextRefreshMillis = currentTimeMillis + _followeeRefreshMillis;
					_handoff_knownFollowees.add(new SchedulableFollowee(followee.followee, nextRefreshMillis));
					refresh = followee.followee;
					refreshNumber = _background_nextOperationNumber;
					_background_nextOperationNumber += 1;
				}
			}
			
			if (shouldNotify)
			{
				this.notifyAll();
			}
			
			// If we don't have any work to do, figure out when something interesting might happen and wait.
			if ((null != publishRoot) || (null != refresh))
			{
				work = new RequestedOperation(publishKeyName
						, publisherKey
						, publishRoot
						, publishNumber
						, refresh
						, refreshNumber
				);
			}
			else
			{
				long nextDueMillis = nextDueRepublishMillis;
				if (!_handoff_knownFollowees.isEmpty())
				{
					nextDueMillis = Math.min(nextDueMillis, _handoff_knownFollowees.peek().nextRefreshMillis);
				}
				Assert.assertTrue(currentTimeMillis < nextDueMillis);
				long millisToWait = nextDueMillis - currentTimeMillis;
				try
				{
					this.wait(millisToWait);
				}
				catch (InterruptedException e)
				{
					// We don't interrupt this thread.
					throw Assert.unexpected(e);
				}
				// In this case, we don't want to terminate so we need to return something but we leave it empty so we just get called with an updated timer.
				work = new RequestedOperation(null, null, null, -1, null, -1);
			}
		}
		return work;
	}

	private boolean _locked_prioritizeFollowee(IpfsKey followeeKey, long scheduledTimeMillis)
	{
		Assert.assertTrue(null != followeeKey);
		
		// We will look for the followee and replace its entry in the scheduler with a last refresh time of 0L so it
		// will be scheduled next.
		boolean didFindFollowee = false;
		boolean didChangeFollowee = false;
		Iterator<SchedulableFollowee> iter = _handoff_knownFollowees.iterator();
		while (iter.hasNext())
		{
			SchedulableFollowee elt = iter.next();
			if (elt.followee.equals(followeeKey))
			{
				// We will disregard this if the scheduled time is already sooner than the requested.
				if (scheduledTimeMillis < elt.nextRefreshMillis)
				{
					// Remove this since we will re-add it.
					iter.remove();
					didChangeFollowee = true;
				}
				didFindFollowee = true;
				break;
			}
		}
		
		if (didChangeFollowee)
		{
			_handoff_knownFollowees.add(new SchedulableFollowee(followeeKey, scheduledTimeMillis));
		}
		return didFindFollowee;
	}


	/**
	 * The interface for external capabilities to avoid this class becoming too reachy and to improve testing.
	 */
	public static interface IOperationRunner
	{
		/**
		 * Requests that the publish of the given newRoot be started (completes asynchronously).
		 * 
		 * @param keyName The name of the home user's key to publish.
		 * @param publicKey The public key of the home user being published.
		 * @param newRoot The new root to publish for this user's key.
		 * @return The asynchronously-completing publish operation (cannot be null).
		 */
		FuturePublish startPublish(String keyName, IpfsKey publicKey, IpfsFile newRoot);
		/**
		 * Runs the refresh of the given followee, synchronously on the calling thread, returning true if there was a
		 * success or false if something went wrong.
		 * This doesn't report any information around what changed, just whether or not the refresh was a "success".
		 * 
		 * @param followeeKey The public key of the followee to refresh.
		 * @return A result describing how the operation completed.
		 */
		OperationResult refreshFollowee(IpfsKey followeeKey);
	}


	/**
	 * Just an enum describing the result of a followee refresh operation.
	 */
	public static enum OperationResult
	{
		/**
		 * The refresh completed normally.
		 */
		SUCCESS,
		/**
		 * There was a failure in the refresh, meaning nothing changed, and it should be retried later.
		 */
		TEMPORARY_FAILURE,
		/**
		 * The refresh completed in incremental mode and there is more work to do.
		 */
		MORE_TO_DO,
	}


	private static record RequestedOperation(String publisherKeyName
			, IpfsKey publisherKey
			, IpfsFile publishTarget
			, int publishNumber
			, IpfsKey followeeKey
			, int followeeNumber
	) {}


	private static record SchedulableFollowee(IpfsKey followee, long nextRefreshMillis) {}

	private static record SchedulableRepublish(String keyName, IpfsKey publicKey, IpfsFile rootElement, long nextPublishMillis) {}
}
