package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network read call.
 * @param <D> The data type returned from the read.
 */
public class FutureRead<D>
{
	private D _data;
	private IpfsConnectionException _connectionException;
	private FailedDeserializationException _decodingException;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The successful result of the request.
	 * @throws IpfsConnectionException The exception which caused the read to fail.
	 * @throws FailedDeserializationException There was a failure in decoding the data after loading.
	 */
	public synchronized D get() throws IpfsConnectionException, FailedDeserializationException
	{
		while ((null == _data) && (null == _connectionException) && (null == _decodingException))
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
		if (null != _connectionException)
		{
			throw _connectionException;
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
	 * Called to set the exception which caused the failure when it was a decoding error.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failureInDecoding(FailedDeserializationException exception)
	{
		_decodingException = exception;
		this.notifyAll();
	}
}
