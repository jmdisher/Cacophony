package com.jeffdisher.cacophony.interactive;

import java.net.URL;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
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
		LocalRecordCache.Element element = _context.recordCache.get(postToResolve);
		if (null != element)
		{
			JsonObject postStruct = new JsonObject();
			postStruct.set("name", element.name());
			postStruct.set("description", element.description());
			postStruct.set("publishedSecondsUtc", element.publishedSecondsUtc());
			postStruct.set("discussionUrl", element.discussionUrl());
			postStruct.set("publisherKey", element.publisherKey());
			postStruct.set("cached", element.isCached());
			if (element.isCached())
			{
				postStruct.set("thumbnailUrl", _urlOrNull(_context.baseUrl, element.thumbnailCid()));
				postStruct.set("videoUrl", _urlOrNull(_context.baseUrl, element.videoCid()));
				postStruct.set("audioUrl", _urlOrNull(_context.baseUrl, element.audioCid()));
			}
			
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(postStruct.toString());
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
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
