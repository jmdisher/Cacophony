package com.jeffdisher.cacophony.logic;

import java.io.PrintStream;


public class Executor
{
	/**
	 * This interface is just used to allow higher-level operation logging.
	 */
	public static interface IOperationLog
	{
		void finish(String finishMessage);
	}


	private final PrintStream _stream;
	private int _nextOperationCounter;

	public Executor(PrintStream stream)
	{
		_stream = stream;
		_nextOperationCounter = 0;
	}

	public void logToConsole(String message)
	{
		_stream.println(message);
	}

	/**
	 * Used to create a logging object associated with this opening message so that the completion of the operation will
	 * be associated with it.
	 * 
	 * @param openingMessage The message to log when opening the log option.
	 * @return An object which can receive the log message for when this is finished.
	 */
	public IOperationLog logOperation(String openingMessage)
	{
		int operationNumber = _nextOperationCounter + 1;
		_nextOperationCounter += 1;
		_stream.println(">" + operationNumber + " " + openingMessage);
		return (finishMessage) -> _stream.println("<" + operationNumber + " " + finishMessage);
	}
}
