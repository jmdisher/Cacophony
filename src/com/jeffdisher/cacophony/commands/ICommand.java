package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * An interface for commands which can be run in a generalized fashion.
 *
 * @param <T> The result type returned from the invocation.
 */
public interface ICommand<T extends ICommand.Result>
{
	/**
	 * Runs the command in the given environment.
	 * 
	 * @param environment The configuration of the system's environment.
	 * @return Extra information about the result (cannot be null).
	 * @throws CacophonyException Something went wrong which prevented success (success, or safe error, always returns).
	 */
	T runInEnvironment(IEnvironment environment) throws CacophonyException;

	/**
	 * The common interface of all result types.
	 */
	public interface Result
	{
	}
}
