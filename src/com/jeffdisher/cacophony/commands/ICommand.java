package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;


public interface ICommand
{
	void scheduleActions(Executor executor, RemoteActions remote, LocalActions local);
}
