package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Creates a new draft with default storage state and returns its default state as a Draft type.
 */
public class POST_Raw_CreateDraft implements IPostRawHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public POST_Raw_CreateDraft(String xsrf, DraftManager draftManager)
	{
		_xsrf = xsrf;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			
			// Generate an ID - should be random so just get some bits from the time.
			int id = Math.abs((int)(System.currentTimeMillis() >> 8L));
			Draft draft = InteractiveHelpers.createNewDraft(_draftManager, id);
			response.getWriter().print(draft.toJson().toString());
		}
	}
}
