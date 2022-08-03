package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.DraftWrapper;


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
}
