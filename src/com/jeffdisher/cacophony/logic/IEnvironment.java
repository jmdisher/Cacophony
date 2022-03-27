package com.jeffdisher.cacophony.logic;


/**
 * The interface provided when running commands, allowing them to access logging facilities and create/load
 * configurations to actually interact with the system.
 */
public interface IEnvironment
{
	/**
	 * This interface is just used to allow higher-level operation logging.
	 */
	public static interface IOperationLog
	{
		void finish(String finishMessage);
	}

	void logToConsole(String message);
	IOperationLog logOperation(String openingMessage);
	LocalConfig getLocalConfig();
}
