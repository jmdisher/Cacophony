package com.jeffdisher.cacophony.scheduler;

import java.util.function.Consumer;

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

	// Note that the _onObserve callback is only run once and can only be set before being observed.
	private boolean _didObserve;
	private Consumer<Boolean> _onObserve;


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
		if (!_didObserve)
		{
			_didObserve = true;
			if (null != _onObserve)
			{
				boolean isSuccess = (null == _exception);
				_onObserve.accept(isSuccess);
				_onObserve = null;
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

	/**
	 * Registers a callback which will be called by the first successful thread to observe the result in get().
	 * This means that the callback will be sent on the thread making that call.
	 * Note that this callback will only be called once and can only be installed before a get() call has returned.
	 * 
	 * @param onObserve The callback to run before returning from the first get() call.
	 */
	public synchronized void registerOnObserve(Consumer<Boolean> onObserve)
	{
		Assert.assertTrue(!_didObserve);
		Assert.assertTrue(null == _onObserve);
		Assert.assertTrue(null != onObserve);
		_onObserve = onObserve;
	}
}
