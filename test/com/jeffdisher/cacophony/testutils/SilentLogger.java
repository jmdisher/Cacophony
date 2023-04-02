package com.jeffdisher.cacophony.testutils;

import com.jeffdisher.cacophony.logic.ILogger;


public class SilentLogger implements ILogger
{
	@Override
	public ILogger logStart(String openingMessage)
	{
		return new SilentLogger();
	}

	@Override
	public void logOperation(String message)
	{
	}

	@Override
	public void logFinish(String finishMessage)
	{
	}

	@Override
	public void logVerbose(String message)
	{
	}

	@Override
	public void logError(String message)
	{
	}
}
