package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;


/**
 * Runs commands on background threads, giving each their own clone of the shared context to avoid pollution between
 * commands.
 * Returns a future which exposes the result of the command and its context.
 */
public class CommandRunner
{
	private final Context _sharedContext;
	private final WorkQueue _queue;
	private final Thread[] _threads;

	/**
	 * Creates a runner in a not-started state.
	 * 
	 * @param sharedContext The context which will be cloned for all command invocations.
	 * @param threadCount The number of background threads to set up.
	 */
	public CommandRunner(Context sharedContext, int threadCount)
	{
		_sharedContext = sharedContext;
		_queue = new WorkQueue();
		_threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; ++i)
		{
			_threads[i] = MiscHelpers.createThread(() -> {
				boolean keepRunning = true;
				while (keepRunning)
				{
					Runnable run = _queue.pollForNext();
					if (null != run)
					{
						run.run();
					}
					else
					{
						keepRunning = false;
					}
				}
			}, "CommandRunner thread #" + i);
		}
	}

	/**
	 * Starts the threads in the command runner.  Before this is called, no commands will be able to run.
	 */
	public void startThreads()
	{
		for (Thread thread : _threads)
		{
			thread.start();
		}
	}

	/**
	 * Shuts down the runner, waiting for all threads to exit before returning.  Note that commands which were enqueued
	 * but not yet run will be abandoned.
	 */
	public void shutdownThreads()
	{
		_queue.shutdown();
		for (Thread thread : _threads)
		{
			try
			{
				thread.join();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}

	/**
	 * Runs the given command against a clone of the runner's shared context and returns a future describing the result
	 * and this context instance.
	 * 
	 * @param <T> The return type of the command.
	 * @param command The command to run.
	 * @return A future describing the command result/error and context where it was executed.
	 */
	public <T extends ICommand.Result> FutureCommand<T> runCommand(ICommand<T> command)
	{
		FutureCommand<T> future = new FutureCommand<>();
		_queue.enqueue(() -> {
			Context one = _sharedContext.cloneWithSelectedKey(_sharedContext.keyName);
			try
			{
				future.setContext(one);
				T result = command.runInContext(one);
				future.success(result);
				// We don't expect this instance to change with the commands currently run in interactive mode.
				Assert.assertTrue(_sharedContext.getSelectedKey() == one.getSelectedKey());
			}
			catch (CacophonyException e)
			{
				future.failure(e);
			}
		});
		return future;
	}
}
