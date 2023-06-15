package com.jeffdisher.cacophony.testutils;

import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * A specialized implementation of the time generator to provide a thread interlock for tests which want precise control
 * over timing.
 */
public class MockTimeGenerator implements LongSupplier
{
	public long currentTimeMillis = 1000L;
	private boolean timeObserved = false;


	@Override
	public synchronized long getAsLong()
	{
		long millis = this.currentTimeMillis;
		this.timeObserved = true;
		this.notifyAll();
		return millis;
	}

	/**
	 * Since time is something we can directly control, we want to use this interlock to ensure that the thread updating
	 * the time can be sure that another thread has observed it.  This is only useful for unit testing scenarios as too
	 * many consumers exist across the entire system.
	 * 
	 * @param millis The amount of time to advance.
	 * @throws InterruptedException Waiting for the observation was observed.
	 */
	public synchronized void incrementTimeAndWaitForObservation(long millis) throws InterruptedException
	{
		Assert.assertTrue(this.timeObserved);
		this.timeObserved = false;
		this.currentTimeMillis += millis;
		while (!this.timeObserved)
		{
			this.wait();
		}
	}
}
