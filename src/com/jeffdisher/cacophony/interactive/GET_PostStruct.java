package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
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
 * (if cached) -thumbnailUrl (string)
 * (if cached) -videoUrl (string)
 */
public class GET_PostStruct implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	
	public GET_PostStruct(IEnvironment environment)
	{
		_environment = environment;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsFile postToResolve = IpfsFile.fromIpfsCid(variables[0]);
		try (IReadingAccess access = StandardAccess.readAccess(_environment))
		{
			IpfsFile lastPublishedIndex = access.getLastRootElement();
			IFolloweeReading followees = access.readableFolloweeData();
			LocalRecordCache cache = access.lazilyLoadFolloweeCache(() -> {
				try
				{
					return LocalRecordCacheBuilder.buildFolloweeCache(access, lastPublishedIndex, followees);
				}
				catch (IpfsConnectionException | FailedDeserializationException e)
				{
					// We return null on error but log this.
					e.printStackTrace();
					return null;
				}
			});
			JsonObject postStruct = JsonGenerationHelpers.postStruct(cache, postToResolve);
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
