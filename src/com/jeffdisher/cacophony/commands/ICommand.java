package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;


public interface ICommand
{
	void scheduleActions(Executor executor, LocalActions local) throws IOException;
}
