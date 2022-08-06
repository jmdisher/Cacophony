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
public record RunCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		DraftManager manager = local.buildDraftManager();
		
		Resource staticResource = Resource.newClassPathResource("resources/site/");
		int port = 8000;
		InteractiveServer.runServerUntilStop(environment, manager, staticResource, port);
	}
}
