package com.jeffdisher.cacophony.logic;


/**
 * The interface for the logging context of a specific operation.
 * This allows high-level logging or activities which can be considered logically related.
 */
public interface ILogger
{
	/**
	 * Starts a nested logging context.
	 * 
	 * @param openingMessage The opening message to log.
	 * @return The logging context for a nested operation.
	 */
	ILogger logStart(String openingMessage);
	/**
	 * Logs a specific part of the operation.
	 * 
	 * @param message The message.
	 */
	void logOperation(String message);
	/**
	 * Closes the logging context at the end of the operation.
	 * 
	 * @param finishMessage The final message to close the log.
	 */
	void logFinish(String finishMessage);
	/**
	 * Log an operation-related message as verbose, meaning some implementations or configurations may suppress it.
	 * 
	 * @param message The message.
	 */
	void logVerbose(String message);
	/**
	 * Logs an error message and sets that an error has occurred, in the logger state.
	 * 
	 * @param message The message to log.
	 */
	void logError(String message);
}
