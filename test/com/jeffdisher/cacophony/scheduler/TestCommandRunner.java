package com.jeffdisher.cacophony.scheduler;

import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.types.CacophonyException;


public class TestCommandRunner
{
	@Test
	public void basicPass() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context, 1);
		runner.startThreads();
		TestCommand command = new TestCommand();
		command.shouldPass = true;
		FutureCommand<None> result = runner.runCommand(command);
		Assert.assertEquals(None.NONE, result.get());
		Assert.assertEquals("name", result.getContext().keyName);
		runner.shutdownThreads();
	}

	@Test
	public void basicError() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context, 1);
		runner.startThreads();
		TestCommand command = new TestCommand();
		command.shouldPass = false;
		FutureCommand<None> result = runner.runCommand(command);
		boolean didThrow;
		try
		{
			result.get();
			didThrow = false;
		}
		catch (CacophonyException e)
		{
			didThrow = true;
		}
		Assert.assertTrue(didThrow);
		Assert.assertEquals("name", result.getContext().keyName);
		runner.shutdownThreads();
	}

	@Test
	public void concurrentCommands() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context, 2);
		runner.startThreads();
		
		// We will send in 4 commands which each use a 3-call barrier to prove that they are run concurrently but also dispatched in order.
		CyclicBarrier barrier1 = new CyclicBarrier(3);
		CyclicBarrier barrier2 = new CyclicBarrier(3);
		TestCommand command1 = new TestCommand(true, barrier1);
		TestCommand command2 = new TestCommand(true, barrier1);
		TestCommand command3 = new TestCommand(true, barrier2);
		TestCommand command4 = new TestCommand(true, barrier2);
		
		FutureCommand<None> result1 = runner.runCommand(command1);
		FutureCommand<None> result2 = runner.runCommand(command2);
		FutureCommand<None> result3 = runner.runCommand(command3);
		FutureCommand<None> result4 = runner.runCommand(command4);
		
		barrier1.await();
		Assert.assertEquals(None.NONE, result1.get());
		Assert.assertEquals("name", result1.getContext().keyName);
		Assert.assertEquals(None.NONE, result2.get());
		Assert.assertEquals("name", result2.getContext().keyName);
		barrier2.await();
		Assert.assertEquals(None.NONE, result3.get());
		Assert.assertEquals("name", result3.getContext().keyName);
		Assert.assertEquals(None.NONE, result4.get());
		Assert.assertEquals("name", result4.getContext().keyName);
		
		runner.shutdownThreads();
	}


	private static Context _buildContext()
	{
		return new Context(null
				, null
				, null
				, null
				, null
				, null
				, new HashMap<>()
				, "name"
		);
	}


	private static class TestCommand implements ICommand<None>
	{
		public boolean shouldPass;
		public CyclicBarrier barrier;
		public TestCommand()
		{
		}
		public TestCommand(boolean shouldPass, CyclicBarrier barrier)
		{
			this.shouldPass = shouldPass;
			this.barrier = barrier;
		}
		@Override
		public None runInContext(Context context) throws CacophonyException
		{
			if (null != this.barrier)
			{
				try
				{
					this.barrier.await();
				}
				catch (InterruptedException e)
				{
					throw new AssertionError(e);
				}
				catch (BrokenBarrierException e)
				{
					throw new AssertionError(e);
				}
			}
			if (this.shouldPass)
			{
				return None.NONE;
			}
			else
			{
				throw new CacophonyException("TEST");
			}
		}
	}
}
