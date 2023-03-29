package com.jeffdisher.cacophony.commands.results;

import com.jeffdisher.cacophony.commands.ICommand;


/**
 * An implementation of the result type which contains no actual information.
 */
public class None implements ICommand.Result
{
	public static final None NONE = new None();

	private None()
	{
	}
}
