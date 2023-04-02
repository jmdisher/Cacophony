package com.jeffdisher.cacophony.commands;

import java.io.File;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.utils.Assert;


public record ElementSubCommand(String mime, File filePath, int height, int width, boolean isSpecialImage) implements ICommand<ICommand.Result>
{
	@Override
	public ICommand.Result runInEnvironment(IEnvironment environment, ILogger logger)
	{
		// This is not supposed to be actually called.
		throw Assert.unreachable();
	}
}
