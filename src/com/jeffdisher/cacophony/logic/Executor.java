package com.jeffdisher.cacophony.logic;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;


public class Executor
{
	private final PrintStream _stream;
	private final Queue<Runnable> _queue;

	public Executor(PrintStream stream)
	{
		_stream = stream;
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

	public void logToConsole(String message)
	{
		_stream.println(message);
	}
}
