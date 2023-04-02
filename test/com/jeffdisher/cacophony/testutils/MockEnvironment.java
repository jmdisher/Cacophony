package com.jeffdisher.cacophony.testutils;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IConfigFileSystem;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public class MockEnvironment implements IEnvironment
{
	public long currentTimeMillis = 1000L;
	private boolean timeObserved = false;

	@Override
	public INetworkScheduler getSharedScheduler()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public DraftManager getSharedDraftManager()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public LocalDataModel getSharedDataModel()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public IConfigFileSystem getConfigFileSystem()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public IConnection getConnection()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public String getKeyName()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public IpfsKey getPublicKey()
	{
		// Not used in test.
		throw Assert.unreachable();
	}

	@Override
	public synchronized long currentTimeMillis()
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
