package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
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
	private final LocalUserInfoCache _userInfoCache;
	
	public GET_UserInfo(IEnvironment environment, LocalUserInfoCache userInfoCache)
	{
		_environment = environment;
		_userInfoCache = userInfoCache;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
		
		// This entry-point (as compared to GET_UnknownUserInfo) is intended for use with already-known users so we will
		// only consult the cache.
		// First, we see if we can satisfy this request from the cache.
		LocalUserInfoCache.Element cached = _userInfoCache.getUserInfo(userToResolve);
		if (null != cached)
		{
			// While this picture CID _should_ be cached, it is possible that it isn't, since this cache is allowed to contain stale and non-cached data references.
			String directFetchUrlRoot;
			try (IReadingAccess access = StandardAccess.readAccess(_environment))
			{
				directFetchUrlRoot = access.getDirectFetchUrlRoot();
			}
			JsonObject userInfo = JsonGenerationHelpers.populateJsonForCachedDescription(cached, directFetchUrlRoot + cached.userPicCid().toSafeString());
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(userInfo.toString());
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
