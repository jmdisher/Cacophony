package com.jeffdisher.cacophony.commands.results;

import java.io.PrintStream;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * An implementation of the result type which is for cases where the operation is incremental and may have more work.
 */
public class Incremental implements ICommand.Result
{
	public final boolean moreToDo;

	/**
	 * Creates the incremental result.
	 * 
	 * @param moreToDo True if the incremental operation left more to do or false if it was completed.
	 */
	public Incremental(boolean moreToDo)
	{
		this.moreToDo = moreToDo;
	}

	@Override
	public IpfsFile getIndexToPublish()
	{
		// This is for operations which don't change the home user's index.
		return null;
	}

	@Override
	public void writeHumanReadable(PrintStream output)
	{
		if (this.moreToDo)
		{
			output.println("More work remains");
		}
		else
		{
			output.println("Operation complete");
		}
	}
}
