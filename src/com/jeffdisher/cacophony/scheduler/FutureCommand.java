package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * Describes the asynchronous result of a Command, passing back its result/error and context where it was executed.
 * 
 * @param <T> The return type of the command.
 */
public class FutureCommand<T>
{
	private Context _context;
	private T _result;
	private CacophonyException _error;

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

	public void setContext(Context context)
	{
		// Can only be set once and must be valid.
		Assert.assertTrue(null != context);
		Assert.assertTrue(null == _context);
		_context = context;
	}

	public Context getContext()
	{
		return _context;
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
