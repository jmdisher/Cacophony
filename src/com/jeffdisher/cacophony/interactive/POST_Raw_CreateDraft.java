package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Creates a new draft with default storage state and returns its default state as a Draft type.
 */
public class POST_Raw_CreateDraft implements ValidatedEntryPoints.POST_Raw
{
	private final Context _context;
	private final DraftManager _draftManager;
	
	public POST_Raw_CreateDraft(Context context, DraftManager draftManager)
	{
		_context = context;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		
		// Generate an ID - should be random so just get some bits from the time.
		int id = Math.abs((int)(_context.currentTimeMillisGenerator.getAsLong() >> 8L));
		Draft draft = InteractiveHelpers.createNewDraft(_draftManager, id);
		response.getWriter().print(draft.toJson().toString());
	}
}
