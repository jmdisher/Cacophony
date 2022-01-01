package com.jeffdisher.cacophony.commands;

import java.io.File;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;


public record ElementSubCommand(String mime, File filePath, String codec, int height, int width) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, RemoteActions remote, LocalActions local)
	{
		// TODO Auto-generated method stub
		
	}
}
