package com.jeffdisher.cacophony.scheduler;

import java.util.LinkedList;
import java.util.Queue;

import com.jeffdisher.cacophony.utils.Assert;


public class WorkQueue
{
	private Queue<Runnable> _queue = new LinkedList<>();
	private boolean _running = true;

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
		return _running
				? _queue.remove()
				: null;
	}
 
	public synchronized void enqueue(Runnable r)
	{
		_queue.add(r);
		this.notify();
	}

	public synchronized void shutdown()
	{
		_running = false;
		this.notifyAll();
	}
}
