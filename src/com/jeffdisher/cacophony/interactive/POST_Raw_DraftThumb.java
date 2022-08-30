package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class POST_Raw_DraftThumb implements IPostRawHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftThumb(String xsrf, DraftManager draftManager)
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
			int height = Integer.parseInt(pathVariables[1]);
			int width = Integer.parseInt(pathVariables[2]);
			// Since we know everything coming through this path is an "image/" mime type, we just pass the second part in the path to avoid having to reencode it.
			String codec = pathVariables[3];
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
}
