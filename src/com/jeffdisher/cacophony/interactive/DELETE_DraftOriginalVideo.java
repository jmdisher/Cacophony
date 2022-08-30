package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class DELETE_DraftOriginalVideo implements IDeleteHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public DELETE_DraftOriginalVideo(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			int draftId = Integer.parseInt(variables[0]);
			try
			{
				boolean didDelete = InteractiveHelpers.deleteOriginalVideo(_draftManager, draftId);
				if (didDelete)
				{
					response.setStatus(HttpServletResponse.SC_OK);
				}
				else
				{
					// If we couldn't delete this, it probably means it didn't exist or something racy is happening
					//  (we don't protect against that case, just report it, since the UI should usually prevent this).
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
			catch (FileNotFoundException e)
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
