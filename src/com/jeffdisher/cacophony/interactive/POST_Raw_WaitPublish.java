package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.IEnvironment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Waits for ending pending publishes to complete, returning 200 when they do.
 */
public class POST_Raw_WaitPublish implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final BackgroundOperations _backgroundOperations;
	
	public POST_Raw_WaitPublish(IEnvironment environment
			, BackgroundOperations backgroundOperations
	)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		IEnvironment.IOperationLog log = _environment.logStart("Waiting for publish to complete...");
		// We can now wait for the publish to complete, now that we have closed all the local state.
		_backgroundOperations.waitForPendingPublish();
		log.logFinish("Done!");
		
		response.setStatus(HttpServletResponse.SC_OK);
	}
}
