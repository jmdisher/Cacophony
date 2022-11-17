package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.cacophony.logic.IEnvironment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Waits for ending pending publishes to complete, returning 200 when they do.
 */
public class POST_Raw_WaitPublish implements IPostRawHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	private final BackgroundOperations _backgroundOperations;
	
	public POST_Raw_WaitPublish(IEnvironment environment
			, String xsrf
			, BackgroundOperations backgroundOperations
	)
	{
		_environment = environment;
		_xsrf = xsrf;
		_backgroundOperations = backgroundOperations;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IEnvironment.IOperationLog log = _environment.logOperation("Waiting for publish to complete...");
			// We can now wait for the publish to complete, now that we have closed all the local state.
			_backgroundOperations.waitForPendingPublish();
			log.finish("Done!");
			
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}
}
