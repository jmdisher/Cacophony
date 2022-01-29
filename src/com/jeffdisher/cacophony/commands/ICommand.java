package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;


/**
 * The interface for commands which can be scheduled to run in an Executor.
 */
public interface ICommand
{
	void scheduleActions(Executor executor, ILocalActions local) throws IOException;
}
