package com.jeffdisher.cacophony.interactive;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This will probably change substantially, over time, but it is intended to be used to track and/or manage background
 * operations being performed when running in interactive mode.
 */
public class BackgroundOperations
{
	private final Thread _background;

	private boolean _keepRunning;
	private int _nextOperationNumber;

	private PendingOperation<IpfsFile> _nextToPublish;
	private FuturePublish _currentPublish;

	// Data related to the listener and how we track active operations for reporting purposes.
	private final Object _listenerMonitor;
	private final Map<Integer, Action> _listenerCapture;
	private IOperationListener _listener;

	public BackgroundOperations(IOperationRunner operations)
	{
		_background = new Thread(() -> {
			RequestedOperation operation = _background_consumeNextOperation();
			while (null != operation)
			{
				FuturePublish publish = operations.startPublish(operation.publishTarget);
				_background_setOperationStarted(operation.publishNumber);
				_background_setInProgressPublish(publish);
				publish.get();
				_background_setOperationEnded(operation.publishNumber);
				operation = _background_consumeNextOperation();
			}
		});
		_nextOperationNumber = 1;
		_listenerMonitor = new Object();
		_listenerCapture = new HashMap<>();
	}

	public void startProcess()
	{
		_keepRunning = true;
		_background.start();
	}

	public void shutdownProcess()
	{
		synchronized (this)
		{
			_keepRunning = false;
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
		
		// We will modify the state and notify under monitor but call-out after.
		int newNumber = -1;
		synchronized (this)
		{
			boolean isNew = (null == _nextToPublish);
			int thisNumber = -1;
			if (isNew)
			{
				thisNumber = _nextOperationNumber;
				_nextOperationNumber += 1;
				// If this is new, we want to emit the enqueue.
				newNumber = thisNumber;
			}
			else
			{
				// We will just reuse the existing number if an operation were waiting.
				thisNumber = _nextToPublish.number;
			}
			_nextToPublish = new PendingOperation<IpfsFile>(rootElement, thisNumber);
			this.notifyAll();
			if (newNumber >= 0)
			{
				_defineOperation(newNumber, "Publish " + rootElement);
			}
		}
		if (newNumber >= 0)
		{
			_setOperationEnqueued(newNumber);
		}
	}

	/**
	 * Blocks until any enqueued publish operations are complete.
	 */
	public synchronized void waitForPendingPublish()
	{
		// Wait until there is nothing pending.
		while(_keepRunning && (null != _currentPublish) && (null != _nextToPublish))
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


	private void _defineOperation(int number, String description)
	{
		synchronized(_listenerMonitor)
		{
			_listenerCapture.put(number, new Action(description));
		}
	}

	private void _setOperationEnqueued(int number)
	{
		synchronized(_listenerMonitor)
		{
			if (null != _listener)
			{
				_listener.operationEnqueued(number, _listenerCapture.get(number).description);
			}
		}
	}

	private synchronized RequestedOperation _background_consumeNextOperation()
	{
		// If we are coming back to find new work, the previous work must be done, so clear it and notify anyone waiting.
		_currentPublish = null;
		this.notifyAll();
		
		// Now, wait for more work.
		while (_keepRunning && (null == _nextToPublish))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't interrupt this thread.
				throw Assert.unexpected(e);
			}
		}
		
		// Consume the next.
		RequestedOperation next = null;
		if (_keepRunning)
		{
			Assert.assertTrue(null != _nextToPublish);
			IpfsFile publish = _nextToPublish.target;
			int publishNumber = _nextToPublish.number;
			next = new RequestedOperation(
					publish
					, publishNumber
			);
			_nextToPublish = null;
			this.notifyAll();
		}
		return next;
	}

	private synchronized void _background_setInProgressPublish(FuturePublish publish)
	{
		Assert.assertTrue(null == _currentPublish);
		_currentPublish = publish;
		this.notifyAll();
	}

	private void _background_setOperationStarted(int number)
	{
		synchronized(_listenerMonitor)
		{
			_listenerCapture.get(number).isStarted = true;
			if (null != _listener)
			{
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
	) {}


	private static record PendingOperation<T>(T target, int number) {}
}
