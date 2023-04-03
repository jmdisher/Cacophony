package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.RemoveEntryFromThisChannelCommand;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Called to remove a post from the home user's data stream (by hash CID).
 */
public class DELETE_Post implements ValidatedEntryPoints.DELETE
{
	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _backgroundOperations;
	private final LocalRecordCache _recordCache;
	private final EntryCacheRegistry _entryRegistry;

	public DELETE_Post(IEnvironment environment
			, ILogger logger
			, BackgroundOperations backgroundOperations
			, LocalRecordCache recordCache
			, EntryCacheRegistry entryRegistry
	)
	{
		_environment = environment;
		_logger = logger;
		_backgroundOperations = backgroundOperations;
		_recordCache = recordCache;
		_entryRegistry = entryRegistry;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsFile postHashToRemove = IpfsFile.fromIpfsCid(pathVariables[0]);
		RemoveEntryFromThisChannelCommand command = new RemoveEntryFromThisChannelCommand(postHashToRemove);
		ChangedRoot result = InteractiveHelpers.runCommandAndHandleErrors(response
				, new ICommand.Context(_environment, _logger, _recordCache, null, null)
				, command
		);
		if (null != result)
		{
			IpfsFile newRoot = result.getIndexToPublish();
			// This should change unless they threw an exception.
			Assert.assertTrue(null != newRoot);
			
			// Delete the entry for anyone listening.
			_entryRegistry.removeLocalElement(postHashToRemove);
			
			// Request a republish.
			_backgroundOperations.requestPublish(newRoot);
		}
	}
}
