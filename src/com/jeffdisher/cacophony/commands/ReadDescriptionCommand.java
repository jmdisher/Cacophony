package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;


public record ReadDescriptionCommand() implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, RemoteActions remote, LocalActions local)
	{
		// TODO Auto-generated method stub
		
	}
}
