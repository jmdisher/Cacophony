package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ElementSubCommand(String mime, File filePath, int height, int width, boolean isSpecialImage) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment, ILocalActions local) throws IOException, CacophonyException
	{
		// This is not supposed to be actually called.
		Assert.unreachable();
	}
}
