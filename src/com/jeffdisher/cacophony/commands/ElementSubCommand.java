package com.jeffdisher.cacophony.commands;

import java.io.File;

import com.jeffdisher.cacophony.utils.Assert;


public record ElementSubCommand(String mime, File filePath, int height, int width, boolean isSpecialImage) implements ICommand<ICommand.Result>
{
	@Override
	public ICommand.Result runInContext(ICommand.Context context)
	{
		// This is not supposed to be actually called.
		throw Assert.unreachable();
	}
}
