package com.jeffdisher.cacophony;

import com.jeffdisher.cacophony.data.LocalDataModel;


/**
 * An implementation of LocalDataModel.ILockingStats which prints information about lock acquisitions which take longer
 * than 100 ms (also logging the last thread to acquire the write-lock, since that is usually the cause).
 */
public class LockingStats implements LocalDataModel.ILockingStats
{
	// If a lock acquire takes longer than this many milliseconds, we will log this to stderr.
	private static final long LOCK_PAUSE_REPORT_MILLIS = 100L;
	// If a lock is held for more than this many milliseconds, we will log this to stderr.
	private static final long LOCK_HOLD_REPORT_MILLIS = 100L;

	// We just initialize this so it is never null since we don't have a way to clear it.
	private Thread _lastWriter = Thread.currentThread();

	// We want to identify calls holding a lock for a long time so we will use a thread local.
	// If this adds a lot of overhead, we may eventually remove it before final release.
	private ThreadLocal<Long> _threadAcquireTime = new ThreadLocal<>();

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
		_threadAcquireTime.set(System.currentTimeMillis());
	}

	@Override
	public void releasedReadLock()
	{
		long current = System.currentTimeMillis();
		long holdMillis = (current - _threadAcquireTime.get());
		if (holdMillis > LOCK_HOLD_REPORT_MILLIS)
		{
			new Throwable("Read lock held for " + holdMillis + " ms").printStackTrace();
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
		_threadAcquireTime.set(System.currentTimeMillis());
	}

	@Override
	public void releasedWriteLock()
	{
		long current = System.currentTimeMillis();
		long holdMillis = (current - _threadAcquireTime.get());
		if (holdMillis > LOCK_HOLD_REPORT_MILLIS)
		{
			new Throwable("Write lock held for " + holdMillis + " ms").printStackTrace();
		}
	}
}
