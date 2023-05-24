package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Called to remove a post from the home user's data stream (by hash CID).
 */
public class DELETE_Post implements ValidatedEntryPoints.DELETE
{
	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;

	public DELETE_Post(CommandRunner runner
			, BackgroundOperations backgroundOperations
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		IpfsFile postHashToRemove = IpfsFile.fromIpfsCid(pathVariables[1]);
		
		RemoveEntryFromThisChannelCommand command = new RemoveEntryFromThisChannelCommand(postHashToRemove);
		InteractiveHelpers.SuccessfulCommand<ChangedRoot> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
				, homePublicKey
		);
		if (null != success)
		{
			ChangedRoot result = success.result();
			Context context = success.context();
			IpfsFile newRoot = result.getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			
			// Delete the entry for anyone listening.
			context.entryRegistry.removeLocalElement(context.getSelectedKey(), postHashToRemove);
			
			// Request a republish.
			_backgroundOperations.requestPublish(context.getSelectedKey(), newRoot);
		}
	}
}
