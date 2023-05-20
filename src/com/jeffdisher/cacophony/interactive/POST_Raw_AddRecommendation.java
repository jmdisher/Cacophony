package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.AddRecommendationCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
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
	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;

	public POST_Raw_AddRecommendation(CommandRunner runner
			, BackgroundOperations backgroundOperations
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey userToAdd = IpfsKey.fromPublicKey(pathVariables[0]);
		
		IpfsKey homePublicKey = _runner.getCurrentHomeKey();
		AddRecommendationCommand command = new AddRecommendationCommand(userToAdd);
		InteractiveHelpers.SuccessfulCommand<ChangedRoot> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
		);
		if (null != result)
		{
			IpfsFile newRoot = result.result().getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			_backgroundOperations.requestPublish(result.context().keyName, newRoot);
		}
	}
}
