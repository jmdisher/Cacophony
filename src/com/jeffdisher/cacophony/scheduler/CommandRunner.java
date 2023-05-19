package com.jeffdisher.cacophony.scheduler;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.Assert;


public class CommandRunner
{
	private final Context _sharedContext;

	public CommandRunner(Context sharedContext)
	{
		_sharedContext = sharedContext;
	}

	/**
	 * Runs the given command against a clone of the runner's shared context and returns a future describing the result
	 * and this context instance.
	 * 
	 * @param <T> The return type of the command.
	 * @param command The command to run.
	 * @return A future describing the command result/error and context where it was executed.
	 */
	public <T extends ICommand.Result> FutureCommand<T> runCommand(ICommand<T> command)
	{
		FutureCommand<T> future = new FutureCommand<>();
		Context one = _sharedContext.cloneWithSelectedKey(_sharedContext.keyName);
		try
		{
			future.setContext(one);
			T result = command.runInContext(one);
			future.success(result);
			// We don't expect this instance to change with the commands currently run in interactive mode.
			Assert.assertTrue(_sharedContext.getSelectedKey() == one.getSelectedKey());
		}
		catch (CacophonyException e)
		{
			future.failure(e);
		}
		return future;
	}
}
