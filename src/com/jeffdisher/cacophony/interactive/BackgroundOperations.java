package com.jeffdisher.cacophony.interactive;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
	private IpfsFile _handoff_currentRoot;
	private long _handoff_lastPublishedMillis;
	private boolean _handoff_isPublishRunning;
	private final PriorityQueue<SchedulableFollowee> _handoff_knownFollowees;
	private long _republishIntervalMillis;
	private long _followeeRefreshMillis;

	// Instance variables which are only used by the background thread (only safe for the background thread).
	private int _background_nextOperationNumber;

	public BackgroundOperations(IEnvironment environment, IOperationRunner operations, HandoffConnector<Integer, String> connector, IpfsFile lastPublished, long republishIntervalMillis, long followeeRefreshMillis)
	{
		_environment = environment;
		_connector = connector;
		_background = MiscHelpers.createThread(() -> {
			RequestedOperation operation = _background_consumeNextOperation(_environment.currentTimeMillis());
			while (null != operation)
			{
				// If we have a publish operation, start that first, since that typically takes a long time.
				FuturePublish publish = null;
				if (null != operation.publishTarget)
				{
					_environment.logToConsole("Background start publish: " + operation.publishTarget);
					publish = operations.startPublish(operation.publishTarget);
					Assert.assertTrue(null != publish);
					_connector.create(operation.publishNumber, "Publish " + operation.publishTarget);
				}
				// If we have a followee to refresh, do that work.
				if (null != operation.followeeKey)
				{
					_environment.logToConsole("Background start refresh: " + operation.followeeKey);
					Runnable refresher = operations.startFolloweeRefresh(operation.followeeKey);
					_connector.create(operation.followeeNumber, "Refresh " + operation.followeeKey);
					refresher.run();
					_connector.destroy(operation.followeeNumber);
					_environment.logToConsole("Background end refresh: " + operation.followeeKey);
				}
				// Now, we can wait for the publish before we go back for more work.
				if (null != publish)
				{
					IpfsConnectionException error = publish.get();
					_connector.destroy(operation.publishNumber);
					_environment.logToConsole("Background end publish: " + operation.publishTarget + ((null == error) ? " SUCCESS" : (" FAILED with " + error)));
				}
				operation = _background_consumeNextOperation(_environment.currentTimeMillis());
			}
		}, "Background Operations");
		_republishIntervalMillis = republishIntervalMillis;
		_followeeRefreshMillis = followeeRefreshMillis;
		
		_background_nextOperationNumber = 1;
		
		_handoff_currentRoot = lastPublished;
		// We will treat our startup as though we have never published before, since this isn't worth tracking in the data store.
		_handoff_lastPublishedMillis = 0L;
		_handoff_knownFollowees = new PriorityQueue<>(1, new Comparator<>() {
			@Override
			public int compare(SchedulableFollowee arg0, SchedulableFollowee arg1)
			{
				// The documentation of Comparator does a terrible job of saying how the order is actually derived from the result here so "if positive -> arg0 comes AFTER arg1").
				// In our case, we want the list to be sorted with the oldest refresh first.  Hence:  Ascending order on the last refresh millis.
				return Long.signum(arg0.lastRefreshMillis - arg1.lastRefreshMillis);
			}
		});
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

	public void requestPublish(IpfsFile rootElement)
	{
		Assert.assertTrue(null != rootElement);
		
		synchronized (this)
		{
			// We just set the root and clear the publish time so the background thread will pick this up.
			_handoff_currentRoot = rootElement;
			_handoff_lastPublishedMillis = 0L;
			this.notifyAll();
		}
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
	public synchronized void waitForPendingPublish()
	{
		// We just want to make sure that the time of publish is non-zero (meaning it was picked up since we last
		// requested it) and that there is no publish pending (since that means it hasn't completed yet).
		while (_handoff_keepRunning && (0L == _handoff_lastPublishedMillis) && _handoff_isPublishRunning)
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
			if (_handoff_isPublishRunning)
			{
				_handoff_isPublishRunning = false;
				shouldNotify = true;
			}
			
			// Determine if we have publish work to do.
			IpfsFile publish = null;
			int publishNumber = -1;
			// Before waiting, we want to see if we should perform any scheduler operations and then look at what kind of delay we should use.
			long dueTimePublishMillis = _handoff_lastPublishedMillis + _republishIntervalMillis;
			if (dueTimePublishMillis <= currentTimeMillis)
			{
				// The republish is due - set the current time as when we updated.
				_handoff_lastPublishedMillis = currentTimeMillis;
				shouldNotify = true;
				publish = _handoff_currentRoot;
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
			if ((null != publish) || (null != refresh))
			{
				work = new RequestedOperation(
						publish
						, publishNumber
						, refresh
						, refreshNumber
				);
			}
			else
			{
				long nextDueMillis = _handoff_lastPublishedMillis + _republishIntervalMillis;
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
				work = new RequestedOperation(null, -1, null, -1);
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
		 * @param newRoot The new root to publish for this user's key.
		 * @return The asynchronously-completing publish operation (cannot be null).
		 */
		FuturePublish startPublish(IpfsFile newRoot);
		/**
		 * Requests that an asynchronous refresh of the given followee be started.  The bulk of the work is expected to
		 * be performed within the returned Runnable.
		 * NOTE:  The reason why this start-run split is done is in order to improve reporting around server state.  The
		 * start allows the creation of the refresh to begin so that it can be reported as running.  The runnable is
		 * where any long-running or blocking operations should be performed.  Once the Runnable returns, the operation
		 * is reported as complete.
		 * 
		 * @param followeeKey The public key of the followee to refresh.
		 * @return The Runnable to perform the bulk of the refresh operation (cannot be null).
		 */
		Runnable startFolloweeRefresh(IpfsKey followeeKey);
	}


	private static record RequestedOperation(
			IpfsFile publishTarget
			, int publishNumber
			, IpfsKey followeeKey
			, int followeeNumber
	) {}


	private static record SchedulableFollowee(IpfsKey followee, long lastRefreshMillis) {}
}
