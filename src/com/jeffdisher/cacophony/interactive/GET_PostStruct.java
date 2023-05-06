package com.jeffdisher.cacophony.interactive;

import java.net.URL;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.ShowPostCommand;
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
		ShowPostCommand command = new ShowPostCommand(postToResolve);
		ShowPostCommand.PostDetails result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			JsonObject postStruct = new JsonObject();
			postStruct.set("name", result.name());
			postStruct.set("description", result.description());
			postStruct.set("publishedSecondsUtc", result.publishedSecondsUtc());
			postStruct.set("discussionUrl", result.discussionUrl());
			postStruct.set("publisherKey", result.publisherKey());
			postStruct.set("cached", result.isKnownToBeCached());
			if (result.isKnownToBeCached())
			{
				postStruct.set("thumbnailUrl", _urlOrNull(_context.baseUrl, result.thumbnailCid()));
				postStruct.set("videoUrl", _urlOrNull(_context.baseUrl, result.videoCid()));
				postStruct.set("audioUrl", _urlOrNull(_context.baseUrl, result.audioCid()));
			}
			
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(postStruct.toString());
		}
	}


	private static String _urlOrNull(URL baseUrl, IpfsFile cid)
	{
		return (null != cid)
				? (baseUrl + cid.toSafeString())
				: null
		;
	}
}
