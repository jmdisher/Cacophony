package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network publish call.
 */
public class FuturePublish
{
	// We store the hash just as additional data so users of the future can ask what it is trying to accomplish.
	private final IpfsFile _hashToPublish;

	private boolean _didSucceed;
	private IpfsConnectionException _exception;

	public FuturePublish(IpfsFile hashToPublish)
	{
		Assert.assertTrue(null != hashToPublish);
		_hashToPublish = hashToPublish;
	}

	/**
	 * @return The hash of the index file the future is attempting to publish (same whether it has finished or not).
	 */
	public IpfsFile getIndexHash()
	{
		return _hashToPublish;
	}

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The exception which caused the error or null if the publish was a success.
	 */
	public synchronized IpfsConnectionException get()
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
		return _exception;
	}

	/**
	 * Called to say that the publish completed successfully.
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
