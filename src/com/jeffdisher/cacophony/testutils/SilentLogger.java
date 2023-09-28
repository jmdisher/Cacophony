package com.jeffdisher.cacophony.testutils;

import com.jeffdisher.cacophony.types.ILogger;


public class SilentLogger implements ILogger
{
	private final SilentLogger _parent;
	private boolean _errorOccurred;

	public SilentLogger()
	{
		_parent = null;
	}

	private SilentLogger(SilentLogger parent)
	{
		_parent = parent;
	}

	@Override
	public ILogger logStart(String openingMessage)
	{
		return new SilentLogger(this);
	}

	@Override
	public void logOperation(String message)
	{
	}

	@Override
	public void logFinish(String finishMessage)
	{
		if (_errorOccurred && (null != _parent))
		{
			_parent._errorOccurred = true;
		}
	}

	@Override
	public void logVerbose(String message)
	{
	}

	@Override
	public void logError(String message)
	{
		_errorOccurred = true;
	}

	@Override
	public boolean didErrorOccur()
	{
		return _errorOccurred;
	}
}
