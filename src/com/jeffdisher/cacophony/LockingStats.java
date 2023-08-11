package com.jeffdisher.cacophony;

import com.jeffdisher.cacophony.data.LocalDataModel;


/**
 * An implementation of LocalDataModel.ILockingStats which prints information about lock acquisitions which take longer
 * than 100 ms (also logging the last thread to acquire the write-lock, since that is usually the cause).
 */
public class LockingStats implements LocalDataModel.ILockingStats
{
	// If a lock acquire takes longer than this many milliseconds, we will log this to stderr error.
	private static final long LOCK_PAUSE_REPORT_MILLIS = 100L;

	// We just initialize this so it is never null since we don't have a way to clear it.
	private Thread _lastWriter = Thread.currentThread();

	@Override
	public long currentTimeMillis()
	{
		return System.currentTimeMillis();
	}

	@Override
	public void acquiredReadLock(long waitMillis)
	{
		if (waitMillis > LOCK_PAUSE_REPORT_MILLIS)
		{
			new Throwable("Read lock acquired in " + waitMillis + " ms.  Last writer: " + _lastWriter.getName()).printStackTrace();
		}
	}

	@Override
	public void acquiredWriteLock(long waitMillis)
	{
		if (waitMillis > LOCK_PAUSE_REPORT_MILLIS)
		{
			new Throwable("Write lock acquired in " + waitMillis + " ms.  Last writer: " + _lastWriter.getName()).printStackTrace();
		}
		_lastWriter = Thread.currentThread();
	}
}
