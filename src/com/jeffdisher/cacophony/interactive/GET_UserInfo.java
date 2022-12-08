package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns build information as a JSON struct:
 * -name
 * -description
 * -userPicUrl
 * -email
 * -website
 */
public class GET_UserInfo implements IGetHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	
	public GET_UserInfo(IEnvironment environment, String xsrf)
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
				IpfsKey publicKey = access.getPublicKey();
				IpfsFile lastPublishedIndex = access.getLastRootElement();
				IFolloweeReading followees = access.readableFolloweeData();
				JsonObject userInfo = JsonGenerationHelpers.userInfo(access, publicKey, lastPublishedIndex, followees, userToResolve);
				if (null != userInfo)
				{
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(userInfo.toString());
				}
				else
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
			catch (IpfsConnectionException | FailedDeserializationException e)
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
