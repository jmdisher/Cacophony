package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;

import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
 * Accepts 2 path arguments:
 * [0] = draft ID
 * [1] = PublishType
 */
public class POST_Raw_DraftPublish implements ValidatedEntryPoints.POST_Raw
{
	private final CommandRunner _runner;
	private final BackgroundOperations _backgroundOperations;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftPublish(CommandRunner runner
			, BackgroundOperations backgroundOperations
			, DraftManager draftManager
	)
	{
		_runner = runner;
		_backgroundOperations = backgroundOperations;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable
	{
		IpfsKey homePublicKey = (IpfsKey)path[2];
		
		int draftId = 0;
		PublishCommand command = null;
		try
		{
			// First, we will use the draft manager to construct the publish command.
			draftId = (Integer)path[3];
			PublishType type = PublishType.valueOf((String)path[4]);
			command = _draftManager.prepareToPublishDraft(draftId
					, (PublishType.VIDEO == type)
					, (PublishType.AUDIO == type)
			);
			
		}
		catch (NumberFormatException e)
		{
			// Draft ID invalid.
			response.getWriter().println("Invalid draft ID: \"" + (String)path[3] + "\"");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		catch (IllegalArgumentException e)
		{
			// Typically thrown by PublishType.valueOf.
			response.getWriter().println("Invalid draft type: \"" + (String)path[4] + "\"");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
		
		if (null != command)
		{
			// Now, run the publish.
			InteractiveHelpers.SuccessfulCommand<OnePost> success = InteractiveHelpers.runCommandAndHandleErrors(response
					, _runner
					, homePublicKey
					, command
					, homePublicKey
			);
			if (null != success)
			{
				// If successful, we delete the draft.
				Assert.assertTrue(draftId > 0);
				InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
				
				// The publish is something we can wait on, asynchronously, in a different call.
				_backgroundOperations.requestPublish(success.context().getSelectedKey(), success.result().getIndexToPublish());
				
				// Return the CID of the new post (for testing).
				response.getWriter().write(success.result().recordCid.toSafeString());
			}
		}
	}


	/**
	 * This type is just used to formalize the interfact but isn't part of the actual data model.
	 */
	private static enum PublishType
	{
		VIDEO,
		TEXT_ONLY,
		AUDIO,
	}
}
