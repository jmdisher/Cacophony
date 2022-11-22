package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

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
public class GET_PostStruct implements IGetHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	
	public GET_PostStruct(IEnvironment environment, String xsrf)
	{
		_environment = environment;
		_xsrf = xsrf;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IpfsFile postToResolve = IpfsFile.fromIpfsCid(variables[0]);
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				IpfsFile lastPublishedIndex = access.getLastRootElement();
				FollowIndex followIndex = access.readOnlyFollowIndex();
				LocalRecordCache cache = access.lazilyLoadFolloweeCache(() -> {
					try
					{
						return JsonGenerationHelpers.buildFolloweeCache(access, lastPublishedIndex, followIndex);
					}
					catch (IpfsConnectionException e)
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
			catch (IpfsConnectionException e)
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				e.printStackTrace(response.getWriter());
			}
			catch (UsageException | VersionException e)
			{
				// Not expected after start-up.
				throw Assert.unexpected(e);
			}
		}
	}
}
