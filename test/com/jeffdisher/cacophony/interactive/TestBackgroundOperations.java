package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.testutils.MockEnvironment;
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

	// The dispatcher is expected to lock-step execution, so we synchronize the call as a simple approach.
	private static final Consumer<Runnable> DISPATCHER = new Consumer<>() {
		@Override
		public void accept(Runnable arg0)
		{
			synchronized (this)
			{
				arg0.run();
			}
		}
	};

	@Test
	public void noOperations() throws Throwable
	{
		StandardEnvironment env = new StandardEnvironment(System.out, null, null, false);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		back.startProcess();
		
		// Enqueue one, then another.
		back.requestPublish(F1);
		ops.returnOn(F1, publish1);
		publish1.success();
		ops.waitForConsume();
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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
		MockEnvironment env = new MockEnvironment();
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
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
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

	@Test
	public void refreshAndPublishWithTimedWait() throws Throwable
	{
		// Unfortunately, since this uses the actual monitor with a timed wait, we need to use some real delays for the test.
		// While we could use an external monitor, it would complicate the code in the BackgroundOperations further, so we won't.
		// The down-side to using this real delay should only be that the test could actually test nothing, if running too slowly (seems to not happen, in practice).
		MockEnvironment env = new MockEnvironment();
		// We will just use a single publish, and will repeat it, but it will always be left with success.
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		int runCount[] = new int[1];
		Runnable refresher = () -> {
			runCount[0] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		statusHandoff.registerListener(listener);
		back.startProcess();
		
		// Enqueue a followee refresh.
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.returnFolloweeOn(K1, refresher);
		
		// Just wait for them to request the initial publish to re-run.
		ops.returnOn(F1, publish);
		ops.waitForConsume();
		
		// Allow time to progress - we are using 10 and 20 for the delays, so just skip it past that.
		env.incrementTimeAndWaitForObservation(20L);
		ops.returnOn(F1, publish);
		ops.returnFolloweeOn(K1, refresher);
		ops.waitForConsume();
		
		back.shutdownProcess();
		
		// We should see each publish and refresh happen twice, so the refresher runs twice and we see 4 starts and ends.
		Assert.assertEquals(2, runCount[0]);
		Assert.assertEquals(4, listener.started);
		Assert.assertEquals(4, listener.ended);
	}

	@Test
	public void requestFolloweeRefresh() throws Throwable
	{
		// Enqueue 2 followees, allow them both to refresh, then request a refresh for one of them and see that it
		// happens immediately.
		MockEnvironment env = new MockEnvironment();
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		
		int runCount[] = new int[2];
		Runnable refresher1 = () -> {
			runCount[0] += 1;
		};
		Runnable refresher2 = () -> {
			runCount[1] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		statusHandoff.registerListener(listener);
		back.startProcess();
		
		// We will only bother with the publish once so run it now.
		ops.returnOn(F1, publish);
		
		// Enqueue the first followee we want and wait for it and the publish to be consumed.
		ops.returnFolloweeOn(K1, refresher1);
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.waitForConsume();
		
		// Enqueue the other followee and wait for it to be consumed.
		ops.returnFolloweeOn(K2, refresher2);
		back.enqueueFolloweeRefresh(K2, 1L);
		ops.waitForConsume();
		
		// Now, request a refresh on the second and wait for it to be consumed.
		ops.returnFolloweeOn(K2, refresher2);
		back.refreshFollowee(K2);
		ops.waitForConsume();
		
		back.shutdownProcess();
		
		// We should see 3 refresh operations, with a start/end pair for each, and a pair for the publish.
		Assert.assertEquals(1, runCount[0]);
		Assert.assertEquals(2, runCount[1]);
		Assert.assertEquals(4, listener.started);
		Assert.assertEquals(4, listener.ended);
	}

	@Test
	public void addRemoveFollowee() throws Throwable
	{
		MockEnvironment env = new MockEnvironment();
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		
		int runCount[] = new int[1];
		Runnable refresher = () -> {
			runCount[0] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(env, ops, statusHandoff, F1, 10L, 20L);
		statusHandoff.registerListener(listener);
		back.startProcess();
		
		// We will only bother with the publish once so run it now.
		ops.returnOn(F1, publish);
		
		// Enqueue the first followee we want and wait for it and the publish to be consumed.
		ops.returnFolloweeOn(K1, refresher);
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.waitForConsume();
		
		// Remove the followee, verify a second attempt to remove fails.
		boolean didRemove = back.removeFollowee(K1);
		Assert.assertTrue(didRemove);
		didRemove = back.removeFollowee(K1);
		Assert.assertFalse(didRemove);
		
		// Now, re-add the followee and make sure it runs.
		ops.returnFolloweeOn(K1, refresher);
		back.enqueueFolloweeRefresh(K1, 1L);
		ops.waitForConsume();
		
		back.shutdownProcess();
		
		// We should see the refresh having run twice.
		Assert.assertEquals(2, runCount[0]);
		// There should be 3 operations:  1 publish and 2 refreshes.
		Assert.assertEquals(3, listener.started);
		Assert.assertEquals(3, listener.ended);
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
