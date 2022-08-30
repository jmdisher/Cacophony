package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the original video for this draft as a WEBM stream.
 */
public class GET_DraftOriginalVideo implements IGetHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public GET_DraftOriginalVideo(String xsrf, DraftManager draftManager)
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
				ServletOutputStream output = response.getOutputStream();
				InteractiveHelpers.writeOriginalVideoToStream(_draftManager, draftId, (String mime, Long byteSize) -> {
					// Called only when the video is found.
					response.setContentType(mime);
					response.setContentLengthLong(byteSize);
					response.setStatus(HttpServletResponse.SC_OK);
				}, output);
			}
			catch (FileNotFoundException e)
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
