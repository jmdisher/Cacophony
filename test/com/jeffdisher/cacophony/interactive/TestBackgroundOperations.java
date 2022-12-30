package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestBackgroundOperations
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");
	private static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey K2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14W");
	private static final IpfsKey K3 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");


	@Test
	public void noOperations() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		back.shutdownProcess();
	}

	@Test
	public void oneOperation() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publish = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		
		// Enqueue one.
		ops.returnOn(F1, publish);
		publish.success();
		
		back.shutdownProcess();
	}

	@Test
	public void sequentialOperations() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publish1 = new FuturePublish(F1);
		FuturePublish publish2 = new FuturePublish(F2);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		
		// Enqueue one, then another.
		back.requestPublish(F1);
		ops.returnOn(F1, publish1);
		publish1.success();
		back.requestPublish(F2);
		ops.returnOn(F2, publish2);
		publish2.success();
		
		back.shutdownProcess();
	}

	@Test
	public void floodedOperations() throws Throwable
	{
		// This one is somewhat non-deterministic in that the first element added may be seen, or could be overwritten.
		// We do know that none of the others will be seen, but then the last will ALWAYS be seen.
		// We will verify this by only setting success on the first and last.
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publishFirst = new FuturePublish(F1);
		FuturePublish publishLast = new FuturePublish(F3);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		TestListener beforeListener = new TestListener();
		back.startProcess();
		statusHandoff.registerListener(beforeListener);
		
		// Enqueue the one which may or may not be seen, then wait to set its success until we have set all the others.
		back.requestPublish(F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		back.requestPublish(F1);
		back.requestPublish(F2);
		back.requestPublish(F3);
		
		publishFirst.success();
		ops.returnOn(F3, publishLast);
		ops.waitForConsume();
		publishLast.success();
		
		back.shutdownProcess();
		
		TestListener afterListener = new TestListener();
		statusHandoff.registerListener(afterListener);
		// The capture from the start should see 2 of each event but the one at the end should see none, since everything should be done.
		Assert.assertEquals(2, beforeListener.started);
		Assert.assertEquals(2, beforeListener.ended);
		Assert.assertEquals(0, afterListener.started);
		Assert.assertEquals(0, afterListener.ended);
	}

	@Test
	public void testPartialListening() throws Throwable
	{
		// We want to enqueue some operations, then install a listener and verify it gets the callbacks for the earliest operations.
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publishFirst = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		
		// Enqueue the first, wait for consume but do not yet set success.
		back.requestPublish(F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		// We can now install the listener and see all the events, since we know it didn't yet succeed.
		TestListener listener = new TestListener();
		statusHandoff.registerListener(listener);
		// Now, allow it to succeed.
		publishFirst.success();
		
		back.shutdownProcess();
		
		Assert.assertEquals(1, listener.started);
		Assert.assertEquals(1, listener.ended);
	}

	@Test
	public void oneRefresh() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publishFirst = new FuturePublish(F1);
		publishFirst.success();
		boolean didRun[] = new boolean[1];
		TestOperations ops = new TestOperations();
		Runnable refresher = () -> {
			Assert.assertFalse(didRun[0]);
			didRun[0] = true;
		};
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		
		// Enqueue one.
		ops.returnOn(F1, publishFirst);
		ops.returnFolloweeOn(K1, refresher);
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.waitForConsume();
		
		back.shutdownProcess();
		Assert.assertTrue(didRun[0]);
	}

	@Test
	public void refreshAndPublish() throws Throwable
	{
		// We will publish, then use the delay that causes to install both a publish and a refresh so we can see what happens when they both run.
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		FuturePublish publishFirst = new FuturePublish(F1);
		FuturePublish publishSecond = new FuturePublish(F2);
		boolean didRun[] = new boolean[1];
		Runnable refresher = () -> {
			Assert.assertFalse(didRun[0]);
			didRun[0] = true;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		statusHandoff.registerListener(listener);
		back.startProcess();
		
		// Enqueue the first, wait for consume but do not yet set success.
		back.requestPublish(F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		// Now we know that the background thread is waiting for success to enqueue the next publish and refresh so the next iteration, it will run both.
		back.requestPublish(F2);
		ops.returnOn(F2, publishSecond);
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.returnFolloweeOn(K1, refresher);
		
		// Now, allow the first publish to complete and wait for everything else to be consumed.
		publishFirst.success();
		ops.waitForConsume();
		publishSecond.success();
		
		back.shutdownProcess();
		
		Assert.assertTrue(didRun[0]);
		Assert.assertEquals(3, listener.started);
		Assert.assertEquals(3, listener.ended);
	}

	@Test
	public void sortingAssumptions() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		// Just make sure that our understanding of the sorting Comparator is correct.
		int didRun[] = new int[1];
		TestOperations ops = new TestOperations();
		// We use a barrier to synchronize between the background and foreground threads so we can set up the next followee refresh value before the previous one finished.
		// (Otherwise, this causes intermittent errors when the background thread goes to request the next refresh but the value we are matching on is still null)
		CyclicBarrier barrier = new CyclicBarrier(2);
		Runnable refresher = () -> {
			try
			{
				barrier.await();
			}
			catch (InterruptedException | BrokenBarrierException e)
			{
				// Not in test.
				Assert.fail();
			}
			didRun[0] += 1;
		};
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>();
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		FuturePublish publishFirst = new FuturePublish(F1);
		ops.returnOn(F1, publishFirst);
		publishFirst.success();
		back.enqueueFolloweeRefresh(K2, 5L);
		back.enqueueFolloweeRefresh(K1, 1L);
		back.enqueueFolloweeRefresh(K3, 10L);
		back.startProcess();
		
		// Enqueue one.
		ops.returnFolloweeOn(K1, refresher);
		ops.waitForConsume();
		ops.returnFolloweeOn(K2, refresher);
		barrier.await();
		ops.waitForConsume();
		ops.returnFolloweeOn(K3, refresher);
		barrier.await();
		ops.waitForConsume();
		barrier.await();
		
		back.shutdownProcess();
		Assert.assertEquals(3, didRun[0]);
	}


	private static class TestOperations implements BackgroundOperations.IOperationRunner
	{
		private IpfsFile _match;
		private FuturePublish _return;
		private IpfsKey _expectedFolloweeKey;
		private Runnable _refresher;
		
		@Override
		public synchronized FuturePublish startPublish(IpfsFile newRoot)
		{
			while (!newRoot.equals(_match))
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
			}
			_match = null;
			FuturePublish publish = _return;
			_return = null;
			
			this.notifyAll();
			return publish;
		}
		@Override
		public synchronized Runnable startFolloweeRefresh(IpfsKey followeeKey)
		{
			Assert.assertEquals(_expectedFolloweeKey, followeeKey);
			_expectedFolloweeKey = null;
			Runnable runnable = _refresher;
			_refresher = null;
			this.notifyAll();
			return runnable;
		}
		@Override
		public void finishFolloweeRefresh(Runnable refresher)
		{
			// Do nothing.
		}
		@Override
		public long currentTimeMillis()
		{
			return 1000L;
		}
		public synchronized void waitForConsume()
		{
			while ((null != _match) || (null != _expectedFolloweeKey))
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					Assert.fail();
				}
			}
		}
		public synchronized void returnOn(IpfsFile file, FuturePublish publish)
		{
			_match = file;
			_return = publish;
			this.notifyAll();
		}
		public synchronized void returnFolloweeOn(IpfsKey expectedFolloweeKey, Runnable refresher)
		{
			_expectedFolloweeKey = expectedFolloweeKey;
			_refresher = refresher;
			this.notifyAll();
		}
	}

	private static class TestListener implements HandoffConnector.IHandoffListener<Integer, String>
	{
		public int started = 0;
		public int ended = 0;
		
		@Override
		public boolean create(Integer key, String value)
		{
			this.started += 1;
			return true;
		}
		@Override
		public boolean update(Integer key, String value)
		{
			// Not used in this case.
			Assert.fail();
			return false;
		}
		@Override
		public boolean destroy(Integer key)
		{
			this.ended += 1;
			return true;
		}
	}
}
