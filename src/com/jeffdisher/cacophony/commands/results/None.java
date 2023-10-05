package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An implementation of the result type which contains no actual information.
 */
public class None implements ICommand.Result
{
	/**
	 * There is no information within this type so we only ever use a shared instance.
	 */
	public static final None NONE = new None();

	private None()
	{
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return null;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		// Do nothing.
	}
}
