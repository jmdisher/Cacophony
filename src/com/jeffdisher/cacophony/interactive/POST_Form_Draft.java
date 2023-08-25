package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.data.local.v4.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Updates the given draft with the included data and returns 200 on success or 404 if not found.
 * Variables (at least one of these must be provided but a missing variable means "no change"):
 * -"NAME": The new name (cannot be empty - technically allowed but not good UI).
 * -"DESCRIPTION": The new description (cannot be empty - technically allowed but not good UI).
 * -"DISCUSSION_URL": The new discussion URL (an empty string will be interpreted as "remove").
 */
public class POST_Form_Draft implements ValidatedEntryPoints.POST_Form
{
	public static final String VAR_NAME = "NAME";
	public static final String VAR_DESCRIPTION = "DESCRIPTION";
	public static final String VAR_DISCUSSION_URL = "DISCUSSION_URL";

	private final DraftManager _draftManager;
	
	public POST_Form_Draft(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws IOException
	{
		int draftId = (Integer)path[1];
		String title = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String discussionUrl = formVariables.getIfSingle(VAR_DISCUSSION_URL);
		if ((null != title)
				|| (null != description)
				|| (null != discussionUrl)
		)
		{
			Draft written = InteractiveHelpers.updateDraftText(_draftManager, draftId, title, description, discussionUrl);
			if (null != written)
			{
				response.setStatus(HttpServletResponse.SC_OK);
			}
			else
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
