package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a pin call.
 * Note that this is largely identical to FuturePublish so these calls which return nothing but success may be
 * coalesced in the future (currently kept distinct just for clarity of intent).
 */
public class FuturePin implements IObservableFuture
{
	// We store the actual file reference as a public field, just because it is a common pattern that this may be needed.
	public final IpfsFile cid;

	private boolean _wasObserved = false;
	private boolean _didSucceed;
	private IpfsConnectionException _exception;

	@Override
	public boolean wasObserved()
	{
		return _wasObserved;
	}

	@Override
	public synchronized void waitForCompletion()
	{
		_waitForCompletion();
	}

	/**
	 * Creates the FuturePin object, setting the given cid as the public "cid" field for future reads.  Note that the
	 * implementation doesn't use this cid as it is only provided for common external usage patterns.
	 * 
	 * @param cid The CID of the resource being pinned.
	 */
	public FuturePin(IpfsFile cid)
	{
		this.cid = cid;
	}

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @throws IpfsConnectionException The exception which caused the pin to fail.
	 */
	public synchronized void get() throws IpfsConnectionException
	{
		_waitForCompletion();
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


	private void _waitForCompletion()
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
		_wasObserved = true;
	}
}
