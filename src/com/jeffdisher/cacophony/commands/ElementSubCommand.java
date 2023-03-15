package com.jeffdisher.cacophony.commands;

import java.io.File;

import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ElementSubCommand(String mime, File filePath, int height, int width, boolean isSpecialImage) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		// This is a sub-command so we should never see it at the top-level.
		throw Assert.unreachable();
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		// This is not supposed to be actually called.
		Assert.unreachable();
	}
}
