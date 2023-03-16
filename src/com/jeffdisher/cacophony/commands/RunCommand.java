package com.jeffdisher.cacophony.commands;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.interactive.InteractiveServer;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * This is the command for running the interactive web server.
 */
public record RunCommand(String _overrideCommand, CommandSelectionMode _commandSelectionMode, int _port) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	public enum CommandSelectionMode
	{
		// Strict mode means that the command given (default or on command-line) is the only one which can be used (ignores front-end).
		STRICT,
		// Dangerous mode allows the user to dynamically configure the connection from the front-end.
		// This mode is considered "dangerous" since it allows the front-end to dictate a command which runs on the host system (hope they never say "rm -rf ~").
		DANGEROUS,
	};

	private static final String DEFAULT_COMMAND = "ffmpeg -i -  -c:v libvpx-vp9 -b:v 256k -filter:v fps=15 -c:a libopus -b:a 32k -f webm  -";

	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
		}
		Resource staticResource = Resource.newClassPathResource("resources/site/");
		boolean canChangeCommand = (CommandSelectionMode.DANGEROUS == _commandSelectionMode);
		String processingCommand = (null != _overrideCommand)
			? _overrideCommand
			: DEFAULT_COMMAND
		;
		InteractiveServer.runServerUntilStop(environment, staticResource, _port, processingCommand, canChangeCommand);
	}
}
