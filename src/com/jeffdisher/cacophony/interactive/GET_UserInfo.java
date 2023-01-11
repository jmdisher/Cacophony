package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

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
public class GET_UserInfo implements ValidatedEntryPoints.GET
{
	private final IEnvironment _environment;
	
	public GET_UserInfo(IEnvironment environment)
	{
		_environment = environment;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
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
		catch (FailedDeserializationException e)
		{
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			e.printStackTrace(response.getWriter());
		}
	}
}
