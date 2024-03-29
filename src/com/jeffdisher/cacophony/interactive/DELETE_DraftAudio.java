package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.data.local.v4.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class DELETE_DraftAudio implements ValidatedEntryPoints.DELETE
{
	private final DraftManager _draftManager;
	
	public DELETE_DraftAudio(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		int draftId = (Integer)path[2];
		try
		{
			boolean didDelete = InteractiveHelpers.deleteAudio(_draftManager, draftId);
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
