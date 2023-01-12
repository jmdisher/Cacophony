package com.jeffdisher.cacophony.interactive;

import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;


public class TestConnectorDispatcher
{
	@Test
	public void testStartStop() throws Throwable
	{
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		dispatcher.shutdown();
	}

	@Test
	public void testOneRunnable() throws Throwable
	{
		CountDownLatch latch = new CountDownLatch(1);
		boolean out[] = new boolean[1];
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		dispatcher.accept(() -> {
			out[0] = true;
			latch.countDown();
		});
		latch.await();
		dispatcher.shutdown();
		Assert.assertTrue(out[0]);
	}

	@Test
	public void testBlockingRunnable() throws Throwable
	{
		// We just want to show that there is a slot for the hand-off so we will block one execution on a second enqueue.
		CountDownLatch latch = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(3);
		int count[] = new int[1];
		Runnable runner = () -> {
			latch.countDown();
			count[0] += 1;
			done.countDown();
		};
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		dispatcher.accept(runner);
		dispatcher.accept(runner);
		latch.await();
		dispatcher.accept(runner);
		done.await();
		dispatcher.shutdown();
		Assert.assertEquals(3, count[0]);
	}

	@Test
	public void testMultiThreadedRunnable() throws Throwable
	{
		int main[] = new int[1];
		int back[] = new int[1];
		int sum[] = new int[1];
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		Thread t = new Thread(() -> {
			for (int i = 0; i < 100; ++i)
			{
				dispatcher.accept(() -> {
					back[0] += 1;
					sum[0] += 1;
				});
			}
		});
		t.start();
		for (int i = 0; i < 100; ++i)
		{
			dispatcher.accept(() -> {
				main[0] += 1;
				sum[0] += 1;
			});
		}
		t.join();
		dispatcher.shutdown();
		Assert.assertEquals(100, main[0]);
		Assert.assertEquals(100, back[0]);
		Assert.assertEquals(200, sum[0]);
	}

	@Test
	public void testExceptionRunnable() throws Throwable
	{
		// NOTE:  We expect this call to write a stack trace to STDERR.
		CountDownLatch latch = new CountDownLatch(1);
		ConnectorDispatcher dispatcher = new ConnectorDispatcher();
		dispatcher.start();
		dispatcher.accept(() -> {
			latch.countDown();
			throw new RuntimeException("EXPECTED EXCEPTION");
		});
		// This will allow us to start running the runnable before we shut down (since then it might not run).
		latch.await();
		dispatcher.shutdown();
	}
}
