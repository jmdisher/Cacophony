package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for the given user, as a JSON array.
 */
public class GET_RecommendedKeys implements IGetHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	
	public GET_RecommendedKeys(IEnvironment environment, String xsrf)
	{
		_environment = environment;
		_xsrf = xsrf;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				IpfsKey publicKey = access.scheduler().getPublicKey();
				IpfsFile lastPublishedIndex = access.getLastRootElement();
				FollowIndex followIndex = access.readOnlyFollowIndex();
				JsonArray keys = JsonGenerationHelpers.recommendedKeys(access, publicKey, lastPublishedIndex, followIndex, userToResolve);
				if (null != keys)
				{
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(keys.toString());
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
