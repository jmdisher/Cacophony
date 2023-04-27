package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network key lookup or create call.
 */
public class FutureKey
{
	private IpfsKey _publicKey;
	private IpfsConnectionException _connectionException;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The public key (never null).
	 * @throws IpfsConnectionException The exception which caused the operation to fail.
	 */
	public synchronized IpfsKey get() throws IpfsConnectionException
	{
		while ((null == _publicKey) && (null == _connectionException))
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
		return _publicKey;
	}

	/**
	 * Called to set the key on success.
	 * 
	 * @param publicKey The key to return.
	 */
	public synchronized void success(IpfsKey publicKey)
	{
		_publicKey = publicKey;;
		this.notifyAll();
	}

	/**
	 * Called to set the exception which caused the failure.
	 * 
	 * @param exception The exception to throw.
	 */
	public synchronized void failureInConnection(IpfsConnectionException exception)
	{
		_connectionException = exception;
		this.notifyAll();
	}
}
