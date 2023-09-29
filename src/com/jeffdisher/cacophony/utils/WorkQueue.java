package com.jeffdisher.cacophony.utils;

import java.util.LinkedList;
import java.util.Queue;

import com.jeffdisher.cacophony.types.IpfsConnectionException;


/**
 * A blocking queue of Runnable objects for use in multi-threaded schedulers.
 */
public class WorkQueue
{
	private Queue<Runnable> _queue = new LinkedList<>();
	private boolean _running = true;

	/**
	 * Polls for the next runnable, blocking until one exists or the queue is shut down.
	 * Note that this will only return null if both the queue is empty and the queue has been shut down.
	 * 
	 * @return The next Runnable or null, if the queue is shut down and drained of work.
	 */
	public synchronized Runnable pollForNext()
	{
		while (_running && _queue.isEmpty())
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
		return !_queue.isEmpty()
				? _queue.remove()
				: null;
	}
 
	/**
	 * Enqueus the next runnable task.
	 * 
	 * @param r The runnable task.
	 * @return True if this was enqueued, false if the receiver has been shut down.
	 */
	public synchronized boolean enqueue(Runnable r)
	{
		if (_running)
		{
			_queue.add(r);
			this.notify();
		}
		return _running;
	}

	/**
	 * Shuts down the queue.  Note that this will cause future calls to enqueue() to fail and will allow any threads
	 * blocked in pollForNext() to drain out with null.
	 */
	public synchronized void shutdown()
	{
		_running = false;
		this.notifyAll();
	}


	/**
	 * A utility function to handle the common case of how to deal with Runnable objects which were "stuck" by shutdown,
	 * not able to be enqueued.  Since the general usage of this class is to run asynchronous network operations, this
	 * utility exists to give them a simple "fake network error" to resolve them.
	 * 
	 * @return A faked connection exception.
	 */
	public static IpfsConnectionException createShutdownError()
	{
		// If a command is stuck when we shut down, we will just synthesize an IpfsConnectionException, since that is generally considered a temporary failure.
		return new IpfsConnectionException("Shutting down", null, null);
	}
}
