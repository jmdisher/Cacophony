package com.jeffdisher.cacophony.interactive;

import java.net.URL;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.ShowPostCommand;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a single post as a JSON struct:
 * -cached (boolean)
 * -name (string)
 * -description (string)
 * -publishedSecondsUtc (long)
 * -discussionUrl (string)
 * -publisherKey (string)
 * -thumbnailUrl (string) - can be null (null if not cached)
 * -videoUrl (string) - can be null (null if not cached)
 * -audioUrl (string) - can be null (null if not cached)
 */
public class GET_PostStruct implements ValidatedEntryPoints.GET
{
	private final CommandRunner _runner;
	
	public GET_PostStruct(CommandRunner runner
	)
	{
		_runner = runner;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsFile postToResolve = IpfsFile.fromIpfsCid(variables[0]);
		// TODO:  Change the forceCache when we add the parameter here.
		boolean forceCache = false;
		ShowPostCommand command = new ShowPostCommand(postToResolve, forceCache);
		InteractiveHelpers.SuccessfulCommand<ShowPostCommand.PostDetails> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, null
				, command
				, null
		);
		if (null != success)
		{
			ShowPostCommand.PostDetails result = success.result();
			Context context = success.context();
			JsonObject postStruct = new JsonObject();
			postStruct.set("name", result.name());
			postStruct.set("description", result.description());
			postStruct.set("publishedSecondsUtc", result.publishedSecondsUtc());
			postStruct.set("discussionUrl", result.discussionUrl());
			postStruct.set("publisherKey", result.publisherKey());
			postStruct.set("cached", result.isKnownToBeCached());
			postStruct.set("thumbnailUrl", _urlOrNull(context.baseUrl, result.thumbnailCid()));
			postStruct.set("videoUrl", _urlOrNull(context.baseUrl, result.videoCid()));
			postStruct.set("audioUrl", _urlOrNull(context.baseUrl, result.audioCid()));
			
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
