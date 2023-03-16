package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The asynchronously-returned result of a network resolve call.
 */
public class FutureResolve
{
	private IpfsFile _file;
	private boolean _didFail;

	/**
	 * Blocks for the asynchronous operation to complete.
	 * 
	 * @return The resolved file for the key or null if it couldn't be resolved.
	 */
	public synchronized IpfsFile get()
	{
		while ((null == _file) && !_didFail)
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
	 */
	public synchronized void failure()
	{
		_didFail = true;
		this.notifyAll();
	}
}
