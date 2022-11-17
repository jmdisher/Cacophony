package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This will probably change substantially, over time, but it is intended to be used to track and/or manage background
 * operations being performed when running in interactive mode.
 */
public class BackgroundOperations
{
	private final IEnvironment _environment;
	private final Object _publishMonitor;
	private FuturePublish _latestPublish;

	public BackgroundOperations(IEnvironment environment)
	{
		_environment = environment;
		_publishMonitor = new Object();
	}

	/**
	 * Blocks on any existing publish operation and then stores the given one.
	 * 
	 * @param publish The new publish operation to store in the background (cannot be NULL).
	 */
	public void waitAndStorePublishOperation(FuturePublish publish)
	{
		Assert.assertTrue(null != publish);
		synchronized (_publishMonitor)
		{
			_lockedBlockAndReleasePublish();
			_latestPublish = publish;
		}
	}

	/**
	 * Blocks on any existing publish operation, removing it once it is complete.
	 */
	public void waitForPendingPublish()
	{
		_lockedBlockAndReleasePublish();
	}


	private void _lockedBlockAndReleasePublish()
	{
		if (null != _latestPublish)
		{
			// We wait for this, under the monitor, since all monitor operations would need to block on us.
			IpfsConnectionException error = _latestPublish.get();
			if (null != error)
			{
				// We don't actually care about the error since publish often has an error.
				_environment.logError("Error on previous publish: " + error.getMessage());
			}
			_latestPublish = null;
		}
	}
}
