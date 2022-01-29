package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;


public record ElementSubCommand(String mime, File filePath, String codec, int height, int width, boolean isSpecialImage) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		// TODO Auto-generated method stub
		
	}
}
