package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveEntry;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.types.IpfsFile;

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
		if (null != postHashToRemove)
		{
			try (IWritingAccess access = StandardAccess.writeAccess(_environment, _logger))
			{
				IpfsFile newRoot = RemoveEntry.run(access, _recordCache, postHashToRemove);
				if (null != newRoot)
				{
					// Delete the entry for anyone listening.
					_entryRegistry.removeLocalElement(postHashToRemove);
					
					// Request a republish.
					_backgroundOperations.requestPublish(newRoot);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				else
				{
					// Unknown post.
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
		}
		else
		{
			// Invalid key.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
