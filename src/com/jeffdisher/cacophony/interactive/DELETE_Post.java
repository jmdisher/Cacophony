package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveEntry;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.logic.IEnvironment;
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
	private final BackgroundOperations _backgroundOperations;
	private final LocalRecordCache _recordCache;
	private final HandoffConnector<IpfsFile, Void> _handoffConnector;

	public DELETE_Post(IEnvironment environment, BackgroundOperations backgroundOperations, LocalRecordCache recordCache, HandoffConnector<IpfsFile, Void> handoffConnector)
	{
		_environment = environment;
		_backgroundOperations = backgroundOperations;
		_recordCache = recordCache;
		_handoffConnector = handoffConnector;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsFile postHashToRemove = IpfsFile.fromIpfsCid(pathVariables[0]);
		if (null != postHashToRemove)
		{
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				IpfsFile newRoot = RemoveEntry.run(access, _recordCache, postHashToRemove);
				if (null != newRoot)
				{
					// Delete the entry for anyone listening.
					_handoffConnector.destroy(postHashToRemove);
					
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
