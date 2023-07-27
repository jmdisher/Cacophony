package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.local.v3.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.types.CidOrNone;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Creates a new draft with default storage state and returns its default state as a Draft type.
 * We take a single path variable which includes either "NONE" or the CID of a StreamRecord to which we are replying
 * (502 if invalid CID).
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
		// Check the replyTo (could be "NONE" string or CID).
		CidOrNone replyTo = CidOrNone.parse(pathVariables[0]);
		// We just use the CidOrNone since it has the correct helper parser.
		_proceed(response, replyTo.cid);
	}


	private void _proceed(HttpServletResponse response, IpfsFile replyTo) throws IOException
	{
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		
		// Generate an ID - should be random so just get some bits from the time.
		int id = Math.abs((int)(_context.currentTimeMillisGenerator.getAsLong() >> 8L));
		Draft draft = InteractiveHelpers.createNewDraft(_draftManager, id, replyTo);
		response.getWriter().print(draft.toJson().toString());
	}
}
