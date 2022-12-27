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

	private IpfsFile _nextToPublish;
	private int  _nextToPublishNumber;
	private FuturePublish _currentOperation;

	// Data related to the listener and how we track active operations for reporting purposes.
	private final Object _listenerMonitor;
	private final Map<Integer, Action> _listenerCapture;
	private IOperationListener _listener;

	public BackgroundOperations(IOperationRunner operations)
	{
		_background = new Thread(() -> {
			int[] out_operationNumber = new int[1];
			IpfsFile target = _background_consumeNextToPublish(out_operationNumber);
			while (null != target)
			{
				FuturePublish publish = operations.startPublish(target);
				_background_setOperationStarted(out_operationNumber[0]);
				_background_setInProgressPublish(publish);
				publish.get();
				_background_setOperationEnded(out_operationNumber[0]);
				target = _background_consumeNextToPublish(out_operationNumber);
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
		int operationNumber = -1;
		synchronized (this)
		{
			boolean isNew = (null == _nextToPublish);
			_nextToPublish = rootElement;
			if (isNew)
			{
				_nextToPublishNumber = _nextOperationNumber;
				_nextOperationNumber += 1;
				operationNumber = _nextToPublishNumber;
			}
			this.notifyAll();
			if (operationNumber >= 0)
			{
				_defineOperation(operationNumber, "Publish " + rootElement);
			}
		}
		if (operationNumber >= 0)
		{
			_setOperationEnqueued(operationNumber);
		}
	}

	/**
	 * Blocks until any enqueued publish operations are complete.
	 */
	public synchronized void waitForPendingPublish()
	{
		// Wait until there is nothing pending.
		while(_keepRunning && (null != _currentOperation) && (null != _nextToPublish))
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

	// (we will just use this C-style pass-by-reference just to avoid defining a tuple for this one case)
	private synchronized IpfsFile _background_consumeNextToPublish(int[] number)
	{
		// If we are coming back to find new work, the previous work must be done, so clear it and notify anyone waiting.
		_currentOperation = null;
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
		IpfsFile next = null;
		if (_keepRunning)
		{
			Assert.assertTrue(null != _nextToPublish);
			next = _nextToPublish;
			number[0] = _nextToPublishNumber;
			_nextToPublish = null;
			this.notifyAll();
		}
		return next;
	}

	private synchronized void _background_setInProgressPublish(FuturePublish publish)
	{
		Assert.assertTrue(null == _currentOperation);
		_currentOperation = publish;
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
}
