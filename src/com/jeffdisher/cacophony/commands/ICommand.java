package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * An interface for commands which can be run in a generalized fashion.
 */
public interface ICommand
{
	void runInEnvironment(IEnvironment environment) throws CacophonyException;
}
