package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a single post as a JSON struct:
 * -cached (boolean)
 * -name (string)
 * -description (string)
 * -publishedSecondsUts (long)
 * -discussionUrl (string)
 * -publisherKey (string)
 * (if cached) -thumbnailUrl (string)
 * (if cached) -videoUrl (string)
 * (if cached) -audioUrl (string)
 */
public class GET_PostStruct implements ValidatedEntryPoints.GET
{
	private final ICommand.Context _context;
	
	public GET_PostStruct(ICommand.Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsFile postToResolve = IpfsFile.fromIpfsCid(variables[0]);
		try (IReadingAccess access = StandardAccess.readAccess(_context))
		{
			JsonObject postStruct = JsonGenerationHelpers.postStruct(access.getDirectFetchUrlRoot(), _context.recordCache, postToResolve);
			if (null != postStruct)
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print(postStruct.toString());
			}
			else
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}
}
