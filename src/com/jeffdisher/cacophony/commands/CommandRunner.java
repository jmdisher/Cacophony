package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;
import com.jeffdisher.cacophony.utils.WorkQueue;


/**
 * Runs commands on background threads, giving each their own clone of the shared context to avoid pollution between
 * commands.
 * Returns a future which exposes the result of the command and its context.
 * The commands are scheduled and started in the order they are presented, except in the case of runBlockedCommand where
 * a command will not be scheduled until no other scheduled or running commands share the same key.
 */
public class CommandRunner
{
	private final Context _sharedContext;
	private final WorkQueue _queue;
	private final Thread[] _threads;
	private final Map<IpfsKey, List<RunningTuple>> _blockedRunnables;

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
		_blockedRunnables = new HashMap<>();
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
	 * @param overrideKey If non-null, will be used to find the key name for the command's context.
	 * @return A future describing the command result/error and context where it was executed (null if override failed).
	 */
	public <T extends ICommand.Result> FutureCommand<T> runCommand(ICommand<T> command, IpfsKey overrideKey)
	{
		IpfsKey keyToChoose = (null != overrideKey)
				? overrideKey
				: _sharedContext.getSelectedKey()
		;
		Context one = _sharedContext.cloneWithSelectedKey(keyToChoose);
		FutureCommand<T> future = new FutureCommand<>(one);
		boolean didEnqueue = _queue.enqueue(() -> {
			try
			{
				T result = command.runInContext(one);
				future.success(result);
			}
			catch (CacophonyException e)
			{
				future.failure(e);
			}
		});
		if (!didEnqueue)
		{
			future.failure(WorkQueue.createShutdownError());
		}
		return future;
	}

	/**
	 * Similar to runCommand but will not enqueue the command to run until there are no commands running or in the queue
	 * which share the same blockingKey.
	 * 
	 * @param <T> The return type of the command.
	 * @param blockingKey The key which this command will treat as a mutex around scheduling and execution.
	 * @param command The command to run.
	 * @param overrideKey If non-null, will be used to find the key name for the command's context.
	 * @return A future describing the command result/error and context where it was executed (null if override failed).
	 */
	public <T extends ICommand.Result> FutureCommand<T> runBlockedCommand(IpfsKey blockingKey, ICommand<T> command, IpfsKey overrideKey)
	{
		IpfsKey keyToChoose = (null != overrideKey)
				? overrideKey
				: _sharedContext.getSelectedKey()
		;
		Context one = _sharedContext.cloneWithSelectedKey(keyToChoose);
		FutureCommand<T> future = new FutureCommand<>(one);
		Runnable runnable = () -> {
			try
			{
				T result = command.runInContext(one);
				future.success(result);
			}
			catch (CacophonyException e)
			{
				future.failure(e);
			}
			finally
			{
				_unblockKey(blockingKey);
			}
		};
		Runnable shutdownRunnable = () -> {
			future.failure(WorkQueue.createShutdownError());
			_unblockKey(blockingKey);
		};
		_enqueueOrBlock(blockingKey, new RunningTuple(runnable, shutdownRunnable));
		return future;
	}


	private synchronized void _enqueueOrBlock(IpfsKey blockingKey, RunningTuple tuple)
	{
		List<RunningTuple> runnables = _blockedRunnables.get(blockingKey);
		if (null == runnables)
		{
			// There is nothing in the queue with this key so we can add the empty blocking queue and enqueue this to the scheduler.
			_blockedRunnables.put(blockingKey, new ArrayList<>());
			boolean didEnqueue = _queue.enqueue(tuple.runnable);
			if (!didEnqueue)
			{
				tuple.shutdownError.run();
			}
		}
		else
		{
			// We are blocked on someone so just enter the blocking queue.
			runnables.add(tuple);
		}
	}

	private synchronized void _unblockKey(IpfsKey blockingKey)
	{
		List<RunningTuple> runnables = _blockedRunnables.get(blockingKey);
		// Since we were running and blocking this queue, this MUST not be null.
		Assert.assertTrue(null != runnables);
		
		// If the queue is empty, just remove it.  Otherwise, enqueue the next element in the work queue.
		if (runnables.isEmpty())
		{
			_blockedRunnables.remove(blockingKey);
		}
		else
		{
			RunningTuple next = runnables.remove(0);
			boolean didEnqueue = _queue.enqueue(next.runnable);
			if (!didEnqueue)
			{
				next.shutdownError.run();
			}
		}
	}


	private static record RunningTuple(Runnable runnable, Runnable shutdownError) {}
}
