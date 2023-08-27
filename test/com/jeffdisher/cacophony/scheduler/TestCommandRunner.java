package com.jeffdisher.cacophony.scheduler;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.results.KeyList;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsKey;


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
		FutureCommand<None> result = runner.runCommand(command, null);
		Assert.assertEquals(None.NONE, result.get());
		Assert.assertEquals(MockKeys.K1, result.context.getSelectedKey());
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
		FutureCommand<None> result = runner.runCommand(command, null);
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
		Assert.assertEquals(MockKeys.K1, result.context.getSelectedKey());
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
		
		FutureCommand<None> result1 = runner.runCommand(command1, null);
		FutureCommand<None> result2 = runner.runCommand(command2, null);
		FutureCommand<None> result3 = runner.runCommand(command3, null);
		FutureCommand<None> result4 = runner.runCommand(command4, null);
		
		barrier1.await();
		Assert.assertEquals(None.NONE, result1.get());
		Assert.assertEquals(MockKeys.K1, result1.context.getSelectedKey());
		Assert.assertEquals(None.NONE, result2.get());
		Assert.assertEquals(MockKeys.K1, result2.context.getSelectedKey());
		barrier2.await();
		Assert.assertEquals(None.NONE, result3.get());
		Assert.assertEquals(MockKeys.K1, result3.context.getSelectedKey());
		Assert.assertEquals(None.NONE, result4.get());
		Assert.assertEquals(MockKeys.K1, result4.context.getSelectedKey());
		
		runner.shutdownThreads();
	}

	@Test
	public void blockingCase() throws Throwable
	{
		Context context = _buildContext();
		CommandRunner runner = new CommandRunner(context, 2);
		
		// We will use a pointer to a shared counter and enqueue some commands before the start the runner.
		// We expect to see the commands observe the numbers in the counters in the correct order.
		// We will also throw in some barriers and non-blocking commands to synthesize back-pressure to make out-of-order execution more likely, if the blocking system isn't working.
		// NOTE:  If there is a problem here, it will likely not be observed 100% of the time, but this order interaction should make issues pretty likely.
		int count1[] = new int[1];
		int count2[] = new int[1];
		CyclicBarrier barrier = new CyclicBarrier(3);
		CountingCommand count1_1 = new CountingCommand(count1, null);
		CountingCommand count1_2 = new CountingCommand(count1, null);
		CountingCommand count1_3 = new CountingCommand(count1, barrier);
		TestCommand blank1 = new TestCommand(true, null);
		CountingCommand count2_1 = new CountingCommand(count2, barrier);
		CountingCommand count2_2 = new CountingCommand(count2, null);
		CountingCommand count2_3 = new CountingCommand(count2, null);
		TestCommand blank2 = new TestCommand(true, null);
		
		FutureCommand<None> f1_1 = runner.runBlockedCommand(MockKeys.K1, count1_1, null);
		FutureCommand<None> f1_2 = runner.runBlockedCommand(MockKeys.K1, count1_2, null);
		FutureCommand<None> f1_3 = runner.runBlockedCommand(MockKeys.K1, count1_3, null);
		FutureCommand<None> fb1 = runner.runCommand(blank1, null);
		FutureCommand<None> f2_1 = runner.runBlockedCommand(MockKeys.K2, count2_1, null);
		FutureCommand<None> f2_2 = runner.runBlockedCommand(MockKeys.K2, count2_2, null);
		FutureCommand<None> f2_3 = runner.runBlockedCommand(MockKeys.K2, count2_3, null);
		FutureCommand<None> fb2 = runner.runCommand(blank2, null);
		runner.startThreads();
		
		// This order is a valid order within the implementation but may change if the implementation changes.
		f1_1.get();
		fb1.get();
		fb2.get();
		f1_2.get();
		barrier.await();
		f1_3.get();
		f2_1.get();
		f2_2.get();
		f2_3.get();
		
		// We verify that everything ran in the correct order by looking at the counter they observed (the blocking semantics should avoid any races or concurrent access).
		Assert.assertEquals(0, count1_1.observedValue);
		Assert.assertEquals(1, count1_2.observedValue);
		Assert.assertEquals(2, count1_3.observedValue);
		Assert.assertEquals(0, count2_1.observedValue);
		Assert.assertEquals(1, count2_2.observedValue);
		Assert.assertEquals(2, count2_3.observedValue);
	}

	@Test
	public void keyOverride() throws Throwable
	{
		Context context = new Context(null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, MockKeys.K1
		);
		CommandRunner runner = new CommandRunner(context, 1);
		runner.startThreads();
		KeyList result1 = runner.runCommand(new KeyCapture(), null).get();
		KeyList result2 = runner.runCommand(new KeyCapture(), MockKeys.K1).get();
		KeyList result3 = runner.runCommand(new KeyCapture(), MockKeys.K2).get();
		Assert.assertEquals(MockKeys.K1, result1.keys[0]);
		Assert.assertEquals(MockKeys.K1, result2.keys[0]);
		Assert.assertEquals(MockKeys.K2, result3.keys[0]);
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
				, null
				, null
				, null
				, MockKeys.K1
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

	private static class CountingCommand implements ICommand<None>
	{
		private final int[] _counterRef;
		private final CyclicBarrier _barrier;
		public int observedValue;
		public CountingCommand(int[] counterRef, CyclicBarrier barrier)
		{
			_counterRef = counterRef;
			_barrier = barrier;
		}
		@Override
		public None runInContext(Context context) throws CacophonyException
		{
			if (null != _barrier)
			{
				try
				{
					_barrier.await();
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
			this.observedValue = _counterRef[0];
			_counterRef[0] = this.observedValue + 1;
			return None.NONE;
		}
	}

	private static class KeyCapture implements ICommand<KeyList>
	{
		@Override
		public KeyList runInContext(Context context) throws CacophonyException
		{
			return new KeyList("name", new IpfsKey[] { context.getSelectedKey() });
		}
	}
}
