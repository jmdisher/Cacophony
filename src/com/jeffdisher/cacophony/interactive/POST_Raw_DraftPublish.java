package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.commands.CommandRunner;
import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.types.IpfsFile;
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
			PublishBuilder builder = new PublishBuilder();
			_draftManager.prepareToPublishDraft(builder
					, draftId
					, (PublishType.VIDEO == type)
					, (PublishType.AUDIO == type)
			);
			command = builder.getCommand();
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


	private static class PublishBuilder implements DraftManager.IPublishBuilder
	{
		private final List<ElementSubCommand> _subCommands = new ArrayList<>();
		private PublishCommand _finishedCommand;
		
		@Override
		public void attach(String mime, File filePath, int height, int width)
		{
			_subCommands.add(new ElementSubCommand(mime, filePath, height, width));
		}
		@Override
		public void complete(String name, String description, String discussionUrl, IpfsFile replyTo, String thumbnailMime, File thumbnailPath)
		{
			Assert.assertTrue(null == _finishedCommand);
			ElementSubCommand[] attachments = _subCommands.toArray((int size) -> new ElementSubCommand[size]);
			_finishedCommand = new PublishCommand(name, description, discussionUrl, replyTo, thumbnailMime, thumbnailPath, attachments);
		}
		public PublishCommand getCommand()
		{
			Assert.assertTrue(null != _finishedCommand);
			return _finishedCommand;
		}
	}
}
