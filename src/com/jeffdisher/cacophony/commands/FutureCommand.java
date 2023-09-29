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

	public FutureCommand(Context context)
	{
		Assert.assertTrue(null != context);
		this.context = context;
	}

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

	public synchronized void success(T result)
	{
		// Can only be set once and must be valid.
		Assert.assertTrue(null != result);
		Assert.assertTrue(null == _result);
		_result = result;
		this.notifyAll();
	}

	public synchronized void failure(CacophonyException error)
	{
		// Can only be set once and must be valid.
		Assert.assertTrue(null != error);
		Assert.assertTrue(null == _error);
		_error = error;
		this.notifyAll();
	}
}
