package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Deletes the given draft and returns 200 on success or 404 if not found.
 */
public class DELETE_Draft implements IDeleteHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public DELETE_Draft(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			int draftId = Integer.parseInt(pathVariables[0]);
			
			try
			{
				InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
				response.setStatus(HttpServletResponse.SC_OK);
			}
			catch (FileNotFoundException e)
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
