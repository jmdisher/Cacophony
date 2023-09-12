package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockTimeGenerator;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestBackgroundOperations
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});
	private static final String KEY_NAME = "key";

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
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.startProcess();
		back.shutdownProcess();
	}

	@Test
	public void oneOperation() throws Throwable
	{
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue one.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publish);
		publish.success();
		
		back.shutdownProcess();
	}

	@Test
	public void sequentialOperations() throws Throwable
	{
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish1 = new FuturePublish(F1);
		FuturePublish publish2 = new FuturePublish(F2);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue one, then another.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publish1);
		publish1.success();
		ops.waitForConsume();
		back.requestPublish(MockKeys.K4, F2);
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
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publishFirst = new FuturePublish(F1);
		FuturePublish publishLast = new FuturePublish(F3);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		TestListener beforeListener = new TestListener();
		back.startProcess();
		statusHandoff.registerListener(beforeListener, 0);
		
		// Enqueue the one which may or may not be seen, then wait to set its success until we have set all the others.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		back.requestPublish(MockKeys.K4, F1);
		back.requestPublish(MockKeys.K4, F2);
		back.requestPublish(MockKeys.K4, F3);
		
		publishFirst.success();
		ops.returnOn(F3, publishLast);
		ops.waitForConsume();
		publishLast.success();
		
		back.shutdownProcess();
		
		TestListener afterListener = new TestListener();
		statusHandoff.registerListener(afterListener, 0);
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
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publishFirst = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue the first, wait for consume but do not yet set success.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		// We can now install the listener and see all the events, since we know it didn't yet succeed.
		TestListener listener = new TestListener();
		statusHandoff.registerListener(listener, 0);
		// Now, allow it to succeed.
		publishFirst.success();
		
		back.shutdownProcess();
		
		Assert.assertEquals(1, listener.started);
		Assert.assertEquals(1, listener.ended);
	}

	@Test
	public void oneRefresh() throws Throwable
	{
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publishFirst = new FuturePublish(F1);
		publishFirst.success();
		boolean didRun[] = new boolean[1];
		TestOperations ops = new TestOperations();
		Consumer<IpfsKey> refresher = (IpfsKey key) -> {
			Assert.assertFalse(didRun[0]);
			didRun[0] = true;
		};
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue one.
		ops.returnOn(F1, publishFirst);
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.waitForConsume();
		
		back.shutdownProcess();
		Assert.assertTrue(didRun[0]);
	}

	@Test
	public void refreshAndPublish() throws Throwable
	{
		// We will publish, then use the delay that causes to install both a publish and a refresh so we can see what happens when they both run.
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publishFirst = new FuturePublish(F1);
		FuturePublish publishSecond = new FuturePublish(F2);
		boolean didRun[] = new boolean[1];
		Consumer<IpfsKey> refresher = (IpfsKey key) -> {
			Assert.assertFalse(didRun[0]);
			didRun[0] = true;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		statusHandoff.registerListener(listener, 0);
		back.startProcess();
		
		// Enqueue the first, wait for consume but do not yet set success.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publishFirst);
		ops.waitForConsume();
		
		// Now we know that the background thread is waiting for success to enqueue the next publish and refresh so the next iteration, it will run both.
		back.requestPublish(MockKeys.K4, F2);
		ops.returnOn(F2, publishSecond);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		
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
		// Just make sure that our understanding of the sorting Comparator is correct.
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		
		// We use a barrier and a reentrant call into the background operations in order to ensure that the next followee refresh value is enqueued before the previous (currently executing) one is finished.
		int didRun[] = new int[1];
		CyclicBarrier barrier = new CyclicBarrier(2);
		@SuppressWarnings("unchecked")
		Consumer<IpfsKey>[] container = new Consumer[1];
		container[0] = (IpfsKey key) -> {
			// We just enqueue the next operation before we return from this call.
			if (key.equals(MockKeys.K1))
			{
				ops.returnFolloweeOn(MockKeys.K2, container[0]);
			}
			else if (key.equals(MockKeys.K2))
			{
				ops.returnFolloweeOn(MockKeys.K3, container[0]);
			}
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
		
		FuturePublish publishFirst = new FuturePublish(F1);
		ops.returnOn(F1, publishFirst);
		publishFirst.success();
		back.enqueueFolloweeRefresh(MockKeys.K2, 5L);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		back.enqueueFolloweeRefresh(MockKeys.K3, 10L);
		back.startProcess();
		
		// Enqueue one.
		ops.returnFolloweeOn(MockKeys.K1, container[0]);
		barrier.await();
		barrier.await();
		barrier.await();
		ops.waitForConsume();
		
		back.shutdownProcess();
		Assert.assertEquals(3, didRun[0]);
	}

	@Test
	public void refreshAndPublishWithTimedWait() throws Throwable
	{
		// Unfortunately, since this uses the actual monitor with a timed wait, we need to use some real delays for the test.
		// While we could use an external monitor, it would complicate the code in the BackgroundOperations further, so we won't.
		// The down-side to using this real delay should only be that the test could actually test nothing, if running too slowly (seems to not happen, in practice).
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		// We will just use a single publish, and will repeat it, but it will always be left with success.
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		int runCount[] = new int[1];
		Consumer<IpfsKey> refresher = (IpfsKey key) -> {
			runCount[0] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		statusHandoff.registerListener(listener, 0);
		back.startProcess();
		
		// Enqueue a followee refresh.
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		
		// Just wait for them to request the initial publish to re-run.
		ops.returnOn(F1, publish);
		ops.waitForConsume();
		
		// Allow time to progress - we are using 10 and 20 for the delays, so just skip it past that.
		generator.incrementTimeAndWaitForObservation(20L);
		ops.returnOn(F1, publish);
		ops.returnFolloweeOn(MockKeys.K1, refresher);
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
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		
		int runCount[] = new int[2];
		Consumer<IpfsKey> refresher1 = (IpfsKey key) -> {
			runCount[0] += 1;
		};
		Consumer<IpfsKey> refresher2 = (IpfsKey key) -> {
			runCount[1] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		statusHandoff.registerListener(listener, 0);
		back.startProcess();
		
		// We will only bother with the publish once so run it now.
		ops.returnOn(F1, publish);
		
		// Enqueue the first followee we want and wait for it and the publish to be consumed.
		ops.returnFolloweeOn(MockKeys.K1, refresher1);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.waitForConsume();
		
		// Enqueue the other followee and wait for it to be consumed.
		ops.returnFolloweeOn(MockKeys.K2, refresher2);
		back.enqueueFolloweeRefresh(MockKeys.K2, 1L);
		ops.waitForConsume();
		
		// Now, request a refresh on the second and wait for it to be consumed.
		ops.returnFolloweeOn(MockKeys.K2, refresher2);
		back.refreshFollowee(MockKeys.K2);
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
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish = new FuturePublish(F1);
		publish.success();
		
		int runCount[] = new int[1];
		Consumer<IpfsKey> refresher = (IpfsKey key) -> {
			runCount[0] += 1;
		};
		TestOperations ops = new TestOperations();
		TestListener listener = new TestListener();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, 10L, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		statusHandoff.registerListener(listener, 0);
		back.startProcess();
		
		// We will only bother with the publish once so run it now.
		ops.returnOn(F1, publish);
		
		// Enqueue the first followee we want and wait for it and the publish to be consumed.
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.waitForConsume();
		
		// Remove the followee, verify a second attempt to remove fails.
		boolean didRemove = back.removeFollowee(MockKeys.K1);
		Assert.assertTrue(didRemove);
		didRemove = back.removeFollowee(MockKeys.K1);
		Assert.assertFalse(didRemove);
		
		// Now, re-add the followee and make sure it runs.
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		ops.waitForConsume();
		
		back.shutdownProcess();
		
		// We should see the refresh having run twice.
		Assert.assertEquals(2, runCount[0]);
		// There should be 3 operations:  1 publish and 2 refreshes.
		Assert.assertEquals(3, listener.started);
		Assert.assertEquals(3, listener.ended);
	}

	@Test
	public void multiChannelPublish() throws Throwable
	{
		// Do a normal start-up with one channel.
		long republishIntervalMillis = 10L;
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, republishIntervalMillis, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue one.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publish);
		publish.success();
		ops.waitForConsume();
		
		// Now that it has run through, slightly update the time and add the other key, then wait for it to run.
		String key2 = "key2";
		generator.currentTimeMillis += 5L;
		back.addChannel(key2, MockKeys.K1, F2);
		ops.returnOn(F2, publish);
		ops.waitForConsume();
		
		// Now, advance the time until we see the first, again.
		generator.currentTimeMillis += 5L;
		ops.returnOn(F1, publish);
		ops.waitForConsume();
		
		back.shutdownProcess();
	}

	@Test
	public void multiChannelDeletes() throws Throwable
	{
		// Do a normal start-up with one channel.
		long republishIntervalMillis = 10L;
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		FuturePublish publish = new FuturePublish(F1);
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, republishIntervalMillis, 20L);
		back.addChannel(KEY_NAME, MockKeys.K4, F1);
		back.startProcess();
		
		// Enqueue one.
		back.requestPublish(MockKeys.K4, F1);
		ops.returnOn(F1, publish);
		publish.success();
		ops.waitForConsume();
		back.removeChannel(MockKeys.K4);
		
		// Now that it has run through, slightly update the time and add the other key, then wait for it to run.
		String key2 = "key2";
		generator.currentTimeMillis += 5L;
		back.addChannel(key2, MockKeys.K1, F2);
		ops.returnOn(F2, publish);
		ops.waitForConsume();
		back.removeChannel(MockKeys.K1);
		
		back.shutdownProcess();
	}

	@Test
	public void channelThrashing() throws Throwable
	{
		// We just want to add/request/remove channels over and over to flush out race conditions.
		long republishIntervalMillis = 10L;
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		TestOperations ops = new TestOperations();
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, republishIntervalMillis, 20L);
		back.startProcess();
		
		for (int i = 0; i < 100; ++i)
		{
			FuturePublish publish = new FuturePublish(F1);
			back.addChannel(KEY_NAME, MockKeys.K4, F1);
			back.requestPublish(MockKeys.K4, F1);
			ops.returnOn(F1, publish);
			ops.waitForConsume();
			back.removeChannel(MockKeys.K4);
			publish.success();
		}
		
		back.shutdownProcess();
	}

	@Test
	public void incrementalScheduling() throws Throwable
	{
		long republishIntervalMillis = 500L;
		// Do a normal start-up with one channel - we will use 500 ms reschedule so that we will see some internal prioritization.
		long followeeRefreshMillis = 500L;
		MockTimeGenerator generator = new MockTimeGenerator();
		SilentLogger logger = new SilentLogger();
		TestOperations ops = new TestOperations();
		ops.incrementalResultCount = 2;
		HandoffConnector<Integer, String> statusHandoff = new HandoffConnector<>(DISPATCHER);
		BackgroundOperations back = new BackgroundOperations(generator, logger, ops, statusHandoff, republishIntervalMillis, followeeRefreshMillis);
		TestListener listener = new TestListener();
		back.startProcess();
		statusHandoff.registerListener(listener, 0);
		
		// Add the new followee with a counter on the calls.
		int callCount[] = new int[1];
		Consumer<IpfsKey> refresher = (IpfsKey key) -> {
			callCount[0] += 1;
		};
		ops.returnFolloweeOn(MockKeys.K1, refresher);
		back.enqueueFolloweeRefresh(MockKeys.K1, 1L);
		
		// Wait until these are done.
		ops.waitForConsume();
		back.removeFollowee(MockKeys.K1);
		
		back.shutdownProcess();
		
		// Check call count: should be 3 since 2 times returned that there was more work to do and then the final one will finish.
		Assert.assertEquals(3, callCount[0]);
		Assert.assertEquals(3, listener.started);
		Assert.assertEquals(3, listener.ended);
	}


	private static class TestOperations implements BackgroundOperations.IOperationRunner
	{
		public int incrementalResultCount;
		private IpfsFile _match;
		private FuturePublish _return;
		private IpfsKey _expectedFolloweeKey;
		private Consumer<IpfsKey> _refresher;
		
		@Override
		public synchronized FuturePublish startPublish(String keyName, IpfsKey publicKey, IpfsFile newRoot)
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
		public synchronized BackgroundOperations.OperationResult refreshFollowee(IpfsKey followeeKey)
		{
			Assert.assertEquals(_expectedFolloweeKey, followeeKey);
			Consumer<IpfsKey> toRun = _refresher;
			BackgroundOperations.OperationResult result;
			if (this.incrementalResultCount > 0)
			{
				this.incrementalResultCount -= 1;
				result = BackgroundOperations.OperationResult.MORE_TO_DO;
			}
			else
			{
				_expectedFolloweeKey = null;
				_refresher = null;
				result = BackgroundOperations.OperationResult.SUCCESS;
			}
			this.notifyAll();
			
			toRun.accept(followeeKey);
			return result;
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
		public synchronized void returnFolloweeOn(IpfsKey expectedFolloweeKey, Consumer<IpfsKey> refresher)
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
		public boolean create(Integer key, String value, boolean isNewest)
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
		@Override
		public boolean specialChanged(String special)
		{
			throw new AssertionError("Not used");
		}
	}
}
