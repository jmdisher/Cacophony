package com.jeffdisher.cacophony.commands;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.cacophony.interactive.InteractiveServer;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;


/**
 * This is the command for running the interactive web server.
 */
public record RunCommand(String _overrideCommand, CommandSelectionMode _commandSelectionMode) implements ICommand
{
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
		LocalConfig local = environment.loadExistingConfig();
		DraftManager manager = local.buildDraftManager();
		
		Resource staticResource = Resource.newClassPathResource("resources/site/");
		int port = 8000;
		boolean canChangeCommand = (CommandSelectionMode.DANGEROUS == _commandSelectionMode);
		String processingCommand = (null != _overrideCommand)
			? _overrideCommand
			: DEFAULT_COMMAND
		;
		InteractiveServer.runServerUntilStop(environment, manager, staticResource, port, processingCommand, canChangeCommand);
	}
}
