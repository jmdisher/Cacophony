package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network size call.
 */
public class FutureSize
{
	private long _sizeInBytes = -1L;
	private IpfsConnectionException _exception;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The size of the file, in bytes.
	 * @throws IpfsConnectionException The exception which caused the lookup to fail.
	 */
	public synchronized long get() throws IpfsConnectionException
	{
		while ((-1L == _sizeInBytes) && (null == _exception))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption in this system.
				throw Assert.unexpected(e);
			}
		}
		if (null != _exception)
		{
			throw _exception;
		}
		return _sizeInBytes;
	}

	/**
	 * Called to set the file size on success.
	 * 
	 * @param sizeInBytes The file size to return.
	 */
	public synchronized void success(long sizeInBytes)
	{
		Assert.assertTrue(sizeInBytes >= 0L);
		_sizeInBytes = sizeInBytes;
		this.notify();
	}

	/**
	 * Called to set the exception which caused the failure.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failure(IpfsConnectionException exception)
	{
		_exception = exception;
		this.notify();
	}
}
