package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network resolve call.
 */
public class FutureResolve
{
	private final IpfsKey _key;
	private IpfsFile _file;
	private IpfsConnectionException _exception;

	public FutureResolve(IpfsKey key)
	{
		_key = key;
	}

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The resolved file (never null).
	 * @throws KeyException There was an error resolving (usually means the key is expired).
	 */
	public synchronized IpfsFile get() throws KeyException
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
			// We create the exception here so that the back-trace is more useful.
			throw new KeyException(_key, _exception);
		}
		return _file;
	}

	/**
	 * Called to set the resolved file on success.
	 * 
	 * @param file The resolved file to return.
	 */
	public synchronized void success(IpfsFile file)
	{
		_file = file;
		this.notifyAll();
	}

	/**
	 * Called to notify the future that the resolve failed.
	 * 
	 * @param error The underlying failure (usually means the key is expired).
	 */
	public synchronized void failure(IpfsConnectionException error)
	{
		_exception = error;
		this.notifyAll();
	}
}
