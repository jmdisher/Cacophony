package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class POST_Raw_DraftThumb implements ValidatedEntryPoints.POST_Raw
{
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftThumb(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		int draftId = Integer.parseInt((String)path[2]);
		int height = Integer.parseInt((String)path[3]);
		int width = Integer.parseInt((String)path[4]);
		// Since we know everything coming through this path is an "image/" mime type, we just pass the second part in the path to avoid having to reencode it.
		String codec = (String)path[5];
		String mime = "image/" + codec;
		
		try
		{
			InputStream input = request.getInputStream();
			InteractiveHelpers.saveThumbnailFromStream(_draftManager, draftId, height, width, mime, input);
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
