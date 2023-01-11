package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns all the active drafts as an array of Draft types.
 */
public class GET_Drafts implements ValidatedEntryPoints.GET
{
	private final DraftManager _draftManager;
	
	public GET_Drafts(DraftManager draftManager)
	{
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		
		// We need to list all the drafts we have.  This is an array of draft types.
		JsonArray draftArray = new JsonArray();
		for (Draft draft : InteractiveHelpers.listDrafts(_draftManager))
		{
			JsonObject serialized = draft.toJson();
			draftArray.add(serialized);
		}
		response.getWriter().print(draftArray.toString());
	}
}
