package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Called to remove a post from the home user's data stream (by hash CID).
 */
public class DELETE_Post implements ValidatedEntryPoints.DELETE
{
	private final Context _context;
	private final BackgroundOperations _backgroundOperations;

	public DELETE_Post(Context context
			, BackgroundOperations backgroundOperations
	)
	{
		_context = context;
		_backgroundOperations = backgroundOperations;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsFile postHashToRemove = IpfsFile.fromIpfsCid(pathVariables[0]);
		RemoveEntryFromThisChannelCommand command = new RemoveEntryFromThisChannelCommand(postHashToRemove);
		ChangedRoot result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			IpfsFile newRoot = result.getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			
			// Delete the entry for anyone listening.
			_context.entryRegistry.removeLocalElement(_context.getSelectedKey(), postHashToRemove);
			
			// Request a republish.
			_backgroundOperations.requestPublish(_context.keyName, newRoot);
		}
	}
}
