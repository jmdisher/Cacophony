package com.jeffdisher.cacophony.commands;

import java.io.File;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is a special-case of ICommand in that it is never run.  It merely exists in this form to generalize the parsing
 * of the publish command.  That is, this is just a convenient container of parameters.
 */
public record ElementSubCommand(String mime, File filePath, int height, int width) implements ICommand<ICommand.Result>
{
	@Override
	public ICommand.Result runInContext(Context context)
	{
		// This is not supposed to be actually called.
		throw Assert.unreachable();
	}
}
