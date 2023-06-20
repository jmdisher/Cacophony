package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network read call.
 * @param <D> The data type returned from the read.
 */
public class FutureSizedRead<D> implements ICommonFutureRead<D>, IObservableFuture
{
	private boolean _wasObserved = false;
	private D _data;
	private IpfsConnectionException _connectionException;
	private SizeConstraintException _sizeConstraintException;
	private FailedDeserializationException _decodingException;

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

	@Override
	public synchronized D get() throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		_waitForCompletion();
		if (null != _connectionException)
		{
			throw _connectionException;
		}
		else if (null != _sizeConstraintException)
		{
			throw _sizeConstraintException;
		}
		else if (null != _decodingException)
		{
			throw _decodingException;
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
		this.notifyAll();
	}

	/**
	 * Called to set the exception which caused the failure when it was an IPFS error.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failureInConnection(IpfsConnectionException exception)
	{
		_connectionException = exception;
		this.notifyAll();
	}

	/**
	 * Called to set the exception which caused the failure when verifying data isn't too big.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failureInSizeCheck(SizeConstraintException exception)
	{
		_sizeConstraintException = exception;
		this.notifyAll();
	}

	/**
	 * Called to set the exception which caused the failure when it was a decoding error.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failureInDecoding(FailedDeserializationException exception)
	{
		_decodingException = exception;
		this.notifyAll();
	}


	private void _waitForCompletion()
	{
		while ((null == _data) && (null == _connectionException) && (null == _sizeConstraintException) && (null == _decodingException))
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
