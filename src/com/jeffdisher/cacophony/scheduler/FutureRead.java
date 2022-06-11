package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network read call.
 * @param <D> The data type returned from the read.
 */
public class FutureRead<D>
{
	private D _data;
	private IpfsConnectionException _exception;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The successful result of the request.
	 * @throws IpfsConnectionException The exception which caused the read to fail.
	 */
	public synchronized D get() throws IpfsConnectionException
	{
		while ((null == _data) && (null == _exception))
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
		return _data;
	}

	/**
	 * Called to set the data read on success.
	 * 
	 * @param data The data result to return.
	 */
	public synchronized void success(D data)
	{
		_data = data;
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
