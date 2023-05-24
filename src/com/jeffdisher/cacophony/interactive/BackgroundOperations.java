package com.jeffdisher.cacophony.interactive;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
	private final IEnvironment _environment;
	private final HandoffConnector<Integer, String> _connector;
	private final Thread _background;

	// Instance variables which are shared between caller thread and background thread (can only be touched on monitor).
	private boolean _handoff_keepRunning;
	private final PriorityQueue<SchedulableFollowee> _handoff_knownFollowees;
	private final Map<IpfsKey, ScheduleableRepublish> _handoff_localChannelsByKey;
	private IpfsKey _handoff_currentlyPublishing;
	private long _republishIntervalMillis;
	private long _followeeRefreshMillis;

	// Instance variables which are only used by the background thread (only safe for the background thread).
	private int _background_nextOperationNumber;

	public BackgroundOperations(IEnvironment environment, ILogger logger, IOperationRunner operations, HandoffConnector<Integer, String> connector, long republishIntervalMillis, long followeeRefreshMillis)
	{
		_environment = environment;
		_connector = connector;
		_background = MiscHelpers.createThread(() -> {
			RequestedOperation operation = _background_consumeNextOperation(_environment.currentTimeMillis());
			while (null != operation)
			{
				// If we have a publish operation, start that first, since that typically takes a long time.
				ILogger publishLog = null;
				FuturePublish publish = null;
				if (null != operation.publishTarget)
				{
					publishLog = logger.logStart("Background start publish: " + operation.publishTarget);
					publish = operations.startPublish(operation.publisherKeyName, operation.publisherKey, operation.publishTarget);
					Assert.assertTrue(null != publish);
					_connector.create(operation.publishNumber, "Publish " + operation.publishTarget);
				}
				// If we have a followee to refresh, do that work.
				if (null != operation.followeeKey)
				{
					ILogger log = logger.logStart("Background start refresh: " + operation.followeeKey);
					_connector.create(operation.followeeNumber, "Refresh " + operation.followeeKey);
					boolean didRefresh = operations.refreshFollowee(operation.followeeKey);
					_connector.destroy(operation.followeeNumber);
					log.logFinish("Background end refresh: " + operation.followeeKey + " -> " + (didRefresh ? "SUCCESS" : "FAILURE"));
				}
				// Now, we can wait for the publish before we go back for more work.
				if (null != publish)
				{
					IpfsConnectionException error = publish.get();
					_connector.destroy(operation.publishNumber);
					publishLog.logFinish("Background end publish: " + operation.publishTarget + ((null == error) ? " SUCCESS" : (" FAILED with " + error)));
				}
				operation = _background_consumeNextOperation(_environment.currentTimeMillis());
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
				return Long.signum(arg0.lastRefreshMillis - arg1.lastRefreshMillis);
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

	public synchronized void addChannel(String keyName, IpfsKey publicKey, IpfsFile rootElement)
	{
		// The same channel should never be redundantly added.
		Assert.assertTrue(!_handoff_localChannelsByKey.containsKey(publicKey));
		// We will treat our startup as though we have never published before, since this isn't worth tracking in the data store.
		long lastPublishMillis = 0L;
		ScheduleableRepublish localChannel = new ScheduleableRepublish(keyName, publicKey, rootElement, lastPublishMillis);
		_handoff_localChannelsByKey.put(publicKey, localChannel);
		this.notifyAll();
	}

	public synchronized void requestPublish(IpfsKey publicKey, IpfsFile rootElement)
	{
		// A publish root must always be provided.
		Assert.assertTrue(null != rootElement);
		// We must already have this registered.
		Assert.assertTrue(_handoff_localChannelsByKey.containsKey(publicKey));
		
		// Just reset the publication time and add the new root.
		long lastPublishMillis = 0L;
		ScheduleableRepublish original = _handoff_localChannelsByKey.remove(publicKey);
		ScheduleableRepublish updated = new ScheduleableRepublish(original.keyName, publicKey, rootElement, lastPublishMillis);
		_handoff_localChannelsByKey.put(publicKey, updated);
		this.notifyAll();
	}

	public void enqueueFolloweeRefresh(IpfsKey followeeKey, long lastRefreshMillis)
	{
		Assert.assertTrue(null != followeeKey);
		
		synchronized (this)
		{
			// We just add this to the priority list of followees and the background will decide what to do with it.
			_handoff_knownFollowees.add(new SchedulableFollowee(followeeKey, lastRefreshMillis));
			this.notifyAll();
		}
	}

	/**
	 * Blocks until any enqueued publish operations are complete.
	 */
	public synchronized void waitForPendingPublish(IpfsKey publicKey)
	{
		// We just want to make sure that the time of publish is non-zero (meaning it was picked up since we last
		// requested it) and that this key isn't currently republishing (since that means it hasn't completed yet).
		// (we need to wait for the publish to finish since the publish time is set when it STARTS, not finishes)
		ScheduleableRepublish ready = _handoff_localChannelsByKey.get(publicKey);
		Assert.assertTrue(null != ready);
		while (_handoff_keepRunning && (0L == ready.lastPublishMillis) && !publicKey.equals(_handoff_currentlyPublishing))
		{
			try
			{
				// Just wait.
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption on the top-level threads.
				throw Assert.unexpected(e);
			}
		}
	}

	public synchronized void intervalsWereUpdated(long republishIntervalMillis, long followeeRefreshMillis)
	{
		// Note that we just update the instance variables but won't reschedule anything already in our system.
		_republishIntervalMillis = republishIntervalMillis;
		_followeeRefreshMillis = followeeRefreshMillis;
	}

	public synchronized boolean refreshFollowee(IpfsKey followeeKey)
	{
		Assert.assertTrue(null != followeeKey);
		
		// We will look for the followee and replace its entry in the scheduler with a last refresh time of 0L so it
		// will be scheduled next.
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
		
		if (didFindFollowee)
		{
			_handoff_knownFollowees.add(new SchedulableFollowee(followeeKey, 0L));
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
	private synchronized RequestedOperation _background_consumeNextOperation(long currentTimeMillis)
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
			for (ScheduleableRepublish thisPublish : _handoff_localChannelsByKey.values())
			{
				// Before waiting, we want to see if we should perform any scheduler operations and then look at what kind of delay we should use.
				long dueTimePublishMillis = thisPublish.lastPublishMillis + _republishIntervalMillis;
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
				ScheduleableRepublish original = _handoff_localChannelsByKey.remove(publisherKey);
				ScheduleableRepublish updated = new ScheduleableRepublish(publishKeyName, publisherKey, original.rootElement, currentTimeMillis);
				_handoff_localChannelsByKey.put(publisherKey, updated);
				_handoff_currentlyPublishing = publisherKey;
				shouldNotify = true;
				publishNumber = _background_nextOperationNumber;
				_background_nextOperationNumber += 1;
			}
			
			// Determine if we have refresh work to do.
			IpfsKey refresh = null;
			int refreshNumber = -1;
			if (!_handoff_knownFollowees.isEmpty())
			{
				long dueTimeRefreshMillis = _handoff_knownFollowees.peek().lastRefreshMillis + _followeeRefreshMillis;
				if (dueTimeRefreshMillis <= currentTimeMillis)
				{
					// The refresh is due - remove this from the list and re-add a new schedulable at the end, with the current time.
					SchedulableFollowee followee = _handoff_knownFollowees.remove();
					_handoff_knownFollowees.add(new SchedulableFollowee(followee.followee, currentTimeMillis));
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
					nextDueMillis = Math.min(nextDueMillis, _handoff_knownFollowees.peek().lastRefreshMillis + _followeeRefreshMillis);
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
		 * @return True if the refresh was a success (although nothing may have changed) or false if there was an error.
		 */
		boolean refreshFollowee(IpfsKey followeeKey);
	}


	private static record RequestedOperation(String publisherKeyName
			, IpfsKey publisherKey
			, IpfsFile publishTarget
			, int publishNumber
			, IpfsKey followeeKey
			, int followeeNumber
	) {}


	private static record SchedulableFollowee(IpfsKey followee, long lastRefreshMillis) {}

	private static record ScheduleableRepublish(String keyName, IpfsKey publicKey, IpfsFile rootElement, long lastPublishMillis) {}
}
