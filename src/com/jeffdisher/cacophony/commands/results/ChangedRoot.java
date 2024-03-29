package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An implementation of the result type which contains no special information beyond the changed root.
 */
public class ChangedRoot implements ICommand.Result
{
	private final IpfsFile _newRoot;

	/**
	 * Creates the result with the given new root.
	 * 
	 * @param newRoot The new root of the channel.
	 */
	public ChangedRoot(IpfsFile newRoot)
	{
		_newRoot = newRoot;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return _newRoot;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		// Do nothing.
	}
}
