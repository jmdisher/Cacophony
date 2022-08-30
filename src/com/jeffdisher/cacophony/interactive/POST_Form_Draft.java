package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Updates the given draft with the included data and returns 200 on success or 404 if not found.
 */
public class POST_Form_Draft implements IPostFormHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public POST_Form_Draft(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			int draftId = Integer.parseInt(pathVariables[0]);
			String title = formVariables.getIfSingle("title");
			String description = formVariables.getIfSingle("description");
			// Note that the discussion URL can be null - empty strings should be made null.
			String discussionUrl = formVariables.getIfSingle("discussionUrl");
			if ((null != discussionUrl) && discussionUrl.isEmpty())
			{
				discussionUrl = null;
			}
			if ((null != title) && !title.isEmpty() && (null != description))
			{
				try
				{
					InteractiveHelpers.updateDraftText(_draftManager, draftId, title, description, discussionUrl);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				catch (FileNotFoundException e)
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
}
