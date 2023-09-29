package com.jeffdisher.cacophony.utils;

import org.junit.Assert;
import org.junit.Test;


public class TestWorkQueue
{
	@Test
	public void startStop() throws Throwable
	{
		WorkQueue queue = new WorkQueue();
		queue.shutdown();
		Assert.assertNull(queue.pollForNext());
	}

	@Test
	public void basicCommands() throws Throwable
	{
		int[] counter = new int[] {0};
		WorkQueue queue = new WorkQueue();
		queue.enqueue(() -> {
			Assert.assertEquals(0, counter[0]);
			counter[0] = 1;
		});
		queue.enqueue(() -> {
			Assert.assertEquals(1, counter[0]);
			counter[0] = 2;
		});
		queue.enqueue(() -> {
			Assert.assertEquals(2, counter[0]);
			counter[0] = 3;
		});
		queue.shutdown();
		Runnable r = queue.pollForNext();
		while (null != r)
		{
			r.run();
			r = queue.pollForNext();
		}
		Assert.assertEquals(3, counter[0]);
	}

	@Test
	public void commandsAfterShutdown() throws Throwable
	{
		int[] counter = new int[] {0};
		WorkQueue queue = new WorkQueue();
		queue.enqueue(() -> {
			Assert.assertEquals(0, counter[0]);
			counter[0] = 1;
		});
		queue.enqueue(() -> {
			Assert.assertEquals(1, counter[0]);
			counter[0] = 2;
		});
		queue.shutdown();
		boolean didEnqueue = queue.enqueue(() -> {
			Assert.assertEquals(2, counter[0]);
			counter[0] = 3;
		});
		Assert.assertFalse(didEnqueue);
		Runnable r = queue.pollForNext();
		while (null != r)
		{
			r.run();
			r = queue.pollForNext();
		}
		Assert.assertEquals(2, counter[0]);
	}
}
