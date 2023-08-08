package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Deletes the given draft and returns 200 on success or 404 if not found.
 */
public class DELETE_Draft implements ValidatedEntryPoints.DELETE
{
	private final DraftManager _draftManager;
	
	public DELETE_Draft(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		int draftId = (Integer)path[1];
		
		try
		{
			if (InteractiveHelpers.deleteExistingDraft(_draftManager, draftId))
			{
				response.setStatus(HttpServletResponse.SC_OK);
			}
			else
			{
				// This means the draft exists but couldn't be deleted since it is in use.
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
