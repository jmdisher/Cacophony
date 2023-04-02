package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.ILogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Waits for ending pending publishes to complete, returning 200 when they do.
 */
public class POST_Raw_WaitPublish implements ValidatedEntryPoints.POST_Raw
{
	private final ILogger _logger;
	private final BackgroundOperations _backgroundOperations;
	
	public POST_Raw_WaitPublish(ILogger logger
			, BackgroundOperations backgroundOperations
	)
	{
		_logger = logger;
		_backgroundOperations = backgroundOperations;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		ILogger log = _logger.logStart("Waiting for publish to complete...");
		// We can now wait for the publish to complete, now that we have closed all the local state.
		_backgroundOperations.waitForPendingPublish();
		log.logFinish("Done!");
		
		response.setStatus(HttpServletResponse.SC_OK);
	}
}
