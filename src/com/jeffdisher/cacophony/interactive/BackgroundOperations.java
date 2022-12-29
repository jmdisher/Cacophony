package com.jeffdisher.cacophony.interactive;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


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
	private final Thread _background;
	private final long _republishIntervalMillis;
	private final long _followeeRefreshMillis;

	// Instance variables which are shared between caller thread and background thread (can only be touched on monitor).
	private boolean _handoff_keepRunning;
	private IpfsFile _handoff_currentRoot;
	private long _handoff_lastPublishedMillis;
	private boolean _handoff_isPublishRunning;
	private final PriorityQueue<SchedulableFollowee> _handoff_knownFollowees;

	// Instance variables which are only used by the background thread (only safe for the background thread).
	private int _background_nextOperationNumber;

	// Data related to the listener and how we track active operations for reporting purposes.
	// Only accessible under the specialized listener monitor.
	private final Object _listenerMonitor;
	private final Map<Integer, Action> _listenerCapture;
	private IOperationListener _listener;

	public BackgroundOperations(IOperationRunner operations, IpfsFile lastPublished, long republishIntervalMillis, long followeeRefreshMillis)
	{
		_background = new Thread(() -> {
			RequestedOperation operation = _background_consumeNextOperation(operations.currentTimeMillis());
			while (null != operation)
			{
				// If we have a publish operation, start that first, since that typically takes a long time.
				FuturePublish publish = null;
				if (null != operation.publishTarget)
				{
					publish = operations.startPublish(operation.publishTarget);
					Assert.assertTrue(null != publish);
					_background_setOperationStarted(operation.publishNumber);
				}
				// If we have a followee to refresh, do that work.
				if (null != operation.followeeKey)
				{
					Runnable refresher = operations.startFolloweeRefresh(operation.followeeKey);
					_background_setOperationStarted(operation.followeeNumber);
					refresher.run();
					operations.finishFolloweeRefresh(refresher);
					_background_setOperationEnded(operation.followeeNumber);
				}
				// Now, we can wait for the publish before we go back for more work.
				if (null != publish)
				{
					publish.get();
					_background_setOperationEnded(operation.publishNumber);
				}
				operation = _background_consumeNextOperation(operations.currentTimeMillis());
			}
		});
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
		
		_listenerMonitor = new Object();
		_listenerCapture = new HashMap<>();
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

	public void setListener(IOperationListener listener)
	{
		synchronized(_listenerMonitor)
		{
			// We will just replace this, assuming the user knows what they are doing.
			_listener = listener;
			for (Map.Entry<Integer, Action> entry : _listenerCapture.entrySet())
			{
				int number = entry.getKey();
				Action action = entry.getValue();
				// Technically, the listener argument can be null or can be set to null in these callbacks so we need to check it in the loop.
				if (null != _listener)
				{
					_listener.operationEnqueued(number, action.description);
				}
				if ((action.isStarted) && (null != _listener))
				{
					_listener.operationStart(number);
				}
			}
		}
	}


	private void _background_defineOperation(int number, String description)
	{
		synchronized(_listenerMonitor)
		{
			_listenerCapture.put(number, new Action(description));
		}
	}

	// Requests work to perform but also is responsible for the core scheduling operations - creating operations from the system described to us and the requests passed in from callers.
	private synchronized RequestedOperation _background_consumeNextOperation(long currentTimeMillis)
	{
		RequestedOperation work = null;
		while (_handoff_keepRunning && (null == work))
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
				_background_defineOperation(publishNumber, "Publish " + publish);
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
					_background_defineOperation(refreshNumber, "Refresh " + refresh);
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
			}
		}
		return work;
	}

	private void _background_setOperationStarted(int number)
	{
		synchronized(_listenerMonitor)
		{
			_listenerCapture.get(number).isStarted = true;
			if (null != _listener)
			{
				// For now, we also synthesize the enqueue of this operation since we aren't distinguishing between enqueue and start.
				_listener.operationEnqueued(number, _listenerCapture.get(number).description);
				_listener.operationStart(number);
			}
		}
	}

	private void _background_setOperationEnded(int number)
	{
		synchronized(_listenerMonitor)
		{
			_listenerCapture.remove(number);
			if (null != _listener)
			{
				_listener.operationEnd(number);
			}
		}
	}


	/**
	 * This is just here to make the testing more concise.
	 */
	public static interface IOperationRunner
	{
		FuturePublish startPublish(IpfsFile newRoot);
		Runnable startFolloweeRefresh(IpfsKey followeeKey);
		void finishFolloweeRefresh(Runnable refresher);
		long currentTimeMillis();
	}


	/**
	 * Callbacks associated with changes of state in the listener.  Note that these calls could be issued on any thread
	 * but they will always be sequentially issued.
	 */
	public static interface IOperationListener
	{
		void operationEnqueued(int number, String description);
		void operationStart(int number);
		void operationEnd(int number);
	}


	private static class Action
	{
		public Action(String description)
		{
			this.description = description;
			this.isStarted = false;
		}
		public String description;
		public boolean isStarted;
	}


	private static record RequestedOperation(
			IpfsFile publishTarget
			, int publishNumber
			, IpfsKey followeeKey
			, int followeeNumber
	) {}


	private static record SchedulableFollowee(IpfsKey followee, long lastRefreshMillis) {}
}
