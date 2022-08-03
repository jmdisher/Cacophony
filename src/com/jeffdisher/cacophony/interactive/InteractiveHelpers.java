package com.jeffdisher.cacophony.interactive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.data.local.v1.SizedElement;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * These are very simple helpers used by the InteractiveServer, only pulled out that they can be understood and tested
 * in a high-level way, without needing to also test the REST server at the same time.
 */
public class InteractiveHelpers
{
	// --- Methods related to draft management.
	public static List<Draft> listDrafts(DraftManager draftManager)
	{
		return draftManager.listAllDrafts();
	}
	public static Draft createNewDraft(DraftManager draftManager, int draftId) throws IOException
	{
		return draftManager.createNewDraft(draftId).loadDraft();
	}
	public static Draft readExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		return draftManager.openExistingDraft(draftId).loadDraft();
	}
	public static Draft updateDraftText(DraftManager draftManager, int draftId, String title, String description, String discussionUrl) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft oldDraft = wrapper.loadDraft();
		Draft newDraft = new Draft(oldDraft.id(), oldDraft.publishedSecondsUtc(), title, description, discussionUrl, oldDraft.thumbnail(), oldDraft.originalVideo(), oldDraft.processedVideo());
		wrapper.saveDraft(newDraft);
		return newDraft;
	}
	public static void deleteExistingDraft(DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		draftManager.deleteExistingDraft(draftId);
	}
	public static void publishExistingDraft(IEnvironment environment, DraftManager draftManager, int draftId) throws FileNotFoundException
	{
		DraftWrapper wrapper = draftManager.openExistingDraft(draftId);
		Draft draft = wrapper.loadDraft();
		SizedElement video = draft.processedVideo();
		File videoFile = wrapper.processedVideo();
		if (null == video)
		{
			video = draft.originalVideo();
			videoFile = wrapper.originalVideo();
		}
		SizedElement thumbnail = draft.thumbnail();
		int elementCount = 0;
		if (null != thumbnail)
		{
			elementCount += 1;
		}
		if (null != video)
		{
			elementCount += 1;
		}
		ElementSubCommand[] subElements = new ElementSubCommand[elementCount];
		int index = 0;
		if (null != thumbnail)
		{
			subElements[index] = new ElementSubCommand(thumbnail.mime(), wrapper.thumbnail(), thumbnail.height(), thumbnail.width(), true);
			index += 1;
		}
		if (null != video)
		{
			subElements[index] = new ElementSubCommand(video.mime(), videoFile, video.height(), video.width(), false);
			elementCount += 1;
		}
		
		PublishCommand command = new PublishCommand(draft.title(), draft.description(), draft.discussionUrl(), subElements);
		try
		{
			command.runInEnvironment(environment);
		}
		catch (IpfsConnectionException e)
		{
			System.err.println("Publish command failed with IpfsConnectionException: " + e.getLocalizedMessage());
			e.printStackTrace();
			throw Assert.unexpected(e);
		}
		catch (CacophonyException e)
		{
			System.err.println("Publish command failed with CacophonyException: " + e.getLocalizedMessage());
			e.printStackTrace();
			throw Assert.unexpected(e);
		}
	}
}
