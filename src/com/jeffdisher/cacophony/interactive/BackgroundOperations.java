package com.jeffdisher.cacophony.interactive;

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
	private IpfsFile _nextToPublish;
	private FuturePublish _currentOperation;

	public BackgroundOperations(IOperationRunner operations)
	{
		_background = new Thread(() -> {
			IpfsFile target = _background_consumeNextToPublish();
			while (null != target)
			{
				FuturePublish publish = operations.startPublish(target);
				_background_setInProgressPublish(publish);
				publish.get();
				target = _background_consumeNextToPublish();
			}
		});
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

	public synchronized void requestPublish(IpfsFile rootElement)
	{
		// We will just over-write whatever the pending element is.
		_nextToPublish = rootElement;
		this.notifyAll();
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


	private synchronized IpfsFile _background_consumeNextToPublish()
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


	/**
	 * This is just here to make the testing more concise.
	 */
	public static interface IOperationRunner
	{
		FuturePublish startPublish(IpfsFile newRoot);
	}
}
