package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Describes the asynchronous result of a Command, passing back its result/error and context where it was executed.
 * 
 * @param <T> The return type of the command.
 */
public class FutureCommand<T>
{
	public final Context context;
	private T _result;
	private CacophonyException _error;

	/**
	 * Creates the future, with the given context as an attribute which can be pulled from it.
	 * 
	 * @param context The context to associate with the future, for later look-up.
	 */
	public FutureCommand(Context context)
	{
		Assert.assertTrue(null != context);
		this.context = context;
	}

	/**
	 * Blocks for the future to complete, before returning success or throwing error.
	 * 
	 * @return The result of the command, on success.
	 * @throws CacophonyException The exception encountered while running the command.
	 */
	public synchronized T get() throws CacophonyException
	{
		while ((null == _result) && (null == _error))
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
		if (null != _error)
		{
			throw _error;
		}
		return _result;
	}

	/**
	 * Sets the future's state to success, notifying anyone blocked.
	 * 
	 * @param result The result of the command.
	 */
	public synchronized void success(T result)
	{
		// Can only be set once and must be valid.
		Assert.assertTrue(null != result);
		Assert.assertTrue(null == _result);
		_result = result;
		this.notifyAll();
	}

	/**
	 * Sets the future's state to failure, notifying anyone blocked.
	 * 
	 * @param error The error which caused the command to fail.
	 */
	public synchronized void failure(CacophonyException error)
	{
		// Can only be set once and must be valid.
		Assert.assertTrue(null != error);
		Assert.assertTrue(null == _error);
		_error = error;
		this.notifyAll();
	}
}
