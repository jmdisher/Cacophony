package com.jeffdisher.cacophony.interactive;

import java.util.function.Consumer;

import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * We want the HandoffConnector instances to actually hand-off between wherever the event originates into a different
 * thread which will actually notify the listeners asynchronously.
 * NOTE:  This is why the HandoffConnector listeners should only be for external listener notifications since we don't
 * want the system to evolve into one of those chaotic message queue executor pipelines (although arguably that is
 * chaotic due to overly-generalized notification mechanisms, not the message queue executor design, itself).
 */
public class ConnectorDispatcher implements Consumer<Runnable>
{
	private final Thread _backgroundThread;
	private boolean _keepRunning;
	private Runnable _nextTask;

	public ConnectorDispatcher()
	{
		_backgroundThread = MiscHelpers.createThread(() -> _backgroundMain(), "ConnectorDispatcher");
	}

	public void start()
	{
		_keepRunning = true;
		_backgroundThread.start();
	}

	public void shutdown()
	{
		synchronized (this)
		{
			_keepRunning = false;
			this.notifyAll();
		}
		try
		{
			_backgroundThread.join();
		}
		catch (InterruptedException e)
		{
			// We don't expect the calling thread to be one which uses interrupts.
			throw Assert.unexpected(e);
		}
	}

	@Override
	public synchronized void accept(Runnable task)
	{
		Assert.assertTrue(null != task);
		
		boolean wasInterrupted = false;
		// Make sure that we are still running and that we don't already have an operation.
		while (_keepRunning && (null != _nextTask))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// While few parts of our systems use interrupts, this mechanism can be called by anyone so we need to
				// be sure we handle this.
				// In this case, we will just record that the interrupt happened since this enqueue should be quick.
				wasInterrupted = true;
			}
		}
		if (_keepRunning)
		{
			_nextTask = task;
			this.notifyAll();
		}
		if (wasInterrupted)
		{
			Thread.currentThread().interrupt();
		}
	}


	private void _backgroundMain()
	{
		Runnable toRun = _backgroundGetNextTask();
		while (null != toRun)
		{
			toRun.run();
			toRun = _backgroundGetNextTask();
		}
	}

	private synchronized Runnable _backgroundGetNextTask()
	{
		// Wait while we are running and there is nothing to do.
		while (_keepRunning && (null == _nextTask))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// This thread doesn't use interruption.
				throw Assert.unexpected(e);
			}
		}
		Runnable toRun = null;
		// We return a task even if we are shutting down, so long as it was already here, in order to make tests more deterministic.
		// This may delay shutdown, slightly, but we will already fail to accept new Runnables after shut-down so this isn't big.
		if (null != _nextTask)
		{
			toRun = _nextTask;
			_nextTask = null;
			this.notifyAll();
		}
		return toRun;
	}
}
