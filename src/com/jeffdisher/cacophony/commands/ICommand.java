package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * An interface for commands which can be run in a generalized fashion.
 */
public interface ICommand
{
	/**
	 * @return True if this command requires the public key name to be specified.
	 */
	boolean requiresKey();

	void runInEnvironment(IEnvironment environment) throws CacophonyException;
}
