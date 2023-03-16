package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network save call.
 */
public class FutureSave
{
	private IpfsFile _file;
	private IpfsConnectionException _exception;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The successful result of the request.
	 * @throws IpfsConnectionException The exception which caused the save to fail.
	 */
	public synchronized IpfsFile get() throws IpfsConnectionException
	{
		while ((null == _file) && (null == _exception))
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
		return _file;
	}

	/**
	 * Called to set the data file on success.
	 * 
	 * @param file The file location to return.
	 */
	public synchronized void success(IpfsFile file)
	{
		_file = file;
		this.notifyAll();
	}

	/**
	 * Called to set the exception which caused the failure.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failure(IpfsConnectionException exception)
	{
		_exception = exception;
		this.notifyAll();
	}
}
