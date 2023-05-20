package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;

import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An interface for commands which can be run in a generalized fashion.
 *
 * @param <T> The result type returned from the invocation.
 */
public interface ICommand<T extends ICommand.Result>
{
	/**
	 * Runs the command in the given context.
	 * 
	 * @param context Resources available to the command.
	 * @return Extra information about the result (cannot be null).
	 * @throws CacophonyException Something went wrong which prevented success (success, or safe error, always returns).
	 */
	T runInContext(Context context) throws CacophonyException;

	/**
	 * The common interface of all result types.
	 */
	public interface Result
	{
		/**
		 * Implemented by commands which modify the local user's index root so that the caller of the command can manage
		 * the publication.
		 * Note that the command is responsible for uploading this and saving it to local storage.  They just don't need
		 * to perform the publish.
		 * 
		 * @return The updated index the command wishes to publish (can be null).
		 */
		IpfsFile getIndexToPublish();
		/**
		 * Asks the result to write a description of itself to the given output.
		 * 
		 * @param output The stream for output.
		 */
		void writeHumanReadable(PrintStream output);
	}
}
