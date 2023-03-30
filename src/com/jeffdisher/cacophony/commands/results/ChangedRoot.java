package com.jeffdisher.cacophony.commands.results;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An implementation of the result type which contains no special information beyond the changed root.
 */
public class ChangedRoot implements ICommand.Result
{
	private final IpfsFile _newRoot;

	public ChangedRoot(IpfsFile newRoot)
	{
		_newRoot = newRoot;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		return _newRoot;
	}
}
