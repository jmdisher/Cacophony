package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a pin call.
 * Note that this is largely identical to FuturePublish so these calls which return nothing but success may be
 * coalesced in the future (currently kept distinct just for clarity of intent).
 */
public class FuturePin
{
	private boolean _didSucceed;
	private IpfsConnectionException _exception;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @throws IpfsConnectionException The exception which caused the pin to fail.
	 */
	public synchronized void get() throws IpfsConnectionException
	{
		while (!_didSucceed && (null == _exception))
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
	}

	/**
	 * Called to say that the pin completed successfully.
	 */
	public synchronized void success()
	{
		_didSucceed = true;
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
