package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.RemoveRecommendationCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Requests that the given public key be removed from the list of recommended users.
 * Returns 200 on success, 400 if the given key is invalid or not recommended.
 */
public class DELETE_RemoveRecommendation implements ValidatedEntryPoints.DELETE
{
	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;

	public DELETE_RemoveRecommendation(CommandRunner runner
			, BackgroundOperations backgroundOperations
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey homePublicKey = (IpfsKey)path[3];
		IpfsKey userToRemove = (IpfsKey)path[4];
		
		RemoveRecommendationCommand command = new RemoveRecommendationCommand(userToRemove);
		InteractiveHelpers.SuccessfulCommand<ChangedRoot> result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != result)
		{
			IpfsFile newRoot = result.result().getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			_backgroundOperations.requestPublish(result.context().getSelectedKey(), newRoot);
		}
	}
}
