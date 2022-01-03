package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;


public record UpdateDescriptionCommand(String name, String description, File picturePath) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		// TODO Auto-generated method stub
		
	}
}
