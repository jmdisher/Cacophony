package com.jeffdisher.cacophony.utils;

import java.io.PrintStream;

import com.jeffdisher.cacophony.types.ILogger;


public class StandardLogger implements ILogger
{
	public static StandardLogger topLogger(PrintStream stream, boolean verbose)
	{
		return new StandardLogger(null, stream, "", verbose);
	}


	// We keep the parent so we can write-back error state after a sub-logger finishes.
	private final StandardLogger _parent;
	private final PrintStream _stream;
	private final String _prefix;
	private final boolean _verbose;
	private int _nextOperationCounter;
	private boolean _errorOccurred;

	private StandardLogger(StandardLogger parent
			, PrintStream stream
			, String prefix
			, boolean verbose
	)
	{
		_parent = parent;
		_stream = stream;
		_prefix = prefix;
		_verbose = verbose;
		_nextOperationCounter = 0;
	}

	@Override
	public ILogger logStart(String openingMessage)
	{
		int operationNumber = _nextOperationCounter + 1;
		_nextOperationCounter += 1;
		String prefix = "" + operationNumber;
		_stream.println(">" + prefix + "> " + openingMessage);
		return new StandardLogger(this, _stream, prefix, _verbose);
	}

	@Override
	public void logOperation(String message)
	{
		_stream.println("=" + _prefix + "= " + message);
	}

	@Override
	public void logFinish(String finishMessage)
	{
		// Saturate to error in the parent.
		if (_errorOccurred && (null != _parent))
		{
			_parent._errorOccurred = true;
		}
		_stream.println("<" + _prefix + "< " + finishMessage);
	}

	@Override
	public void logVerbose(String message)
	{
		if (_verbose)
		{
			_stream.println("*" + _prefix + "* " + message);
		}
	}

	@Override
	public void logError(String message)
	{
		System.err.println(message);
		_errorOccurred = true;
	}

	@Override
	public boolean didErrorOccur()
	{
		return _errorOccurred;
	}
}
