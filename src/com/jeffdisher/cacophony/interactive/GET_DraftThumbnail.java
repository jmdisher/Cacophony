package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the thumbnail for this draft as a JPEG.
 */
public class GET_DraftThumbnail implements ValidatedEntryPoints.GET
{
	private final DraftManager _draftManager;
	
	public GET_DraftThumbnail(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		int draftId = Integer.parseInt(variables[0]);
		try
		{
			ServletOutputStream output = response.getOutputStream();
			InteractiveHelpers.loadThumbnailToStream(_draftManager, draftId, (String mime) -> {
				// This is called only on success.
				response.setContentType(mime);
				response.setStatus(HttpServletResponse.SC_OK);
			}, output);
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
