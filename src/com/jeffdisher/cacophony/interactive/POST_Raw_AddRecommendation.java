package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given public key be added to the list of recommended users.
 * Returns 200 on success, 400 if the given key is invalid or already recommended.
 */
public class POST_Raw_AddRecommendation implements ValidatedEntryPoints.POST_Raw
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddRecommendation(IEnvironment environment
			, ILogger logger
			, BackgroundOperations backgroundOperations
	)
	{
		_environment = environment;
		_logger = logger;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		
		AddRecommendationCommand command = new AddRecommendationCommand(userToAdd);
		ChangedRoot result = InteractiveHelpers.runCommandAndHandleErrors(response
				, new ICommand.Context(_environment, _logger, null, null, null)
				, command
		);
		if (null != result)
		{
			IpfsFile newRoot = result.getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			_backgroundOperations.requestPublish(newRoot);
		}
	}
}
