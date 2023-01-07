package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the given draft as a Draft type.
 */
public class GET_Draft implements IGetHandler
{
	private final String _xsrf;
	private final DraftManager _draftManager;
	
	public GET_Draft(String xsrf, DraftManager draftManager)
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
			Draft draft = InteractiveHelpers.readExistingDraft(_draftManager, draftId);
			if (null != draft)
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print(draft.toJson().toString());
			}
			else
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
