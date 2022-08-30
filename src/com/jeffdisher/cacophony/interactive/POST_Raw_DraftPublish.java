package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IEnvironment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
 */
public class POST_Raw_DraftPublish implements IPostRawHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftPublish(IEnvironment environment, String xsrf, DraftManager draftManager)
	{
		_environment = environment;
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
				InteractiveHelpers.publishExistingDraft(_environment, _draftManager, draftId);
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
