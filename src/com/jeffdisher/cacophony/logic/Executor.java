package com.jeffdisher.cacophony.logic;

import java.util.LinkedList;
import java.util.Queue;


public class Executor
{
	private final Queue<Runnable> _queue;

	public Executor()
	{
		_queue = new LinkedList<>();
	}

	public void scheduleRunnable(Runnable r)
	{
		_queue.add(r);
	}

	public void fatalError(Exception e)
	{
		e.printStackTrace();
	}

	public void waitForCompletion()
	{
		while (!_queue.isEmpty())
		{
			Runnable r = _queue.poll();
			r.run();
		}
	}
}
