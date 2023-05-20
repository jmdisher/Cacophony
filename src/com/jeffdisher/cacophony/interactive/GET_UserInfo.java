package com.jeffdisher.cacophony.interactive;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.commands.Context;
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
	private final Context _context;
	
	public GET_UserInfo(Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		IpfsKey userToResolve = IpfsKey.fromPublicKey(variables[0]);
		
		// This entry-point (as compared to GET_UnknownUserInfo) is intended for use with already-known users so we will
		// only consult the cache.
		// First, we see if we can satisfy this request from the cache.
		LocalUserInfoCache.Element cached = _context.userInfoCache.getUserInfo(userToResolve);
		if (null != cached)
		{
			// While this picture CID _should_ be cached, it is possible that it isn't, since this cache is allowed to contain stale and non-cached data references.
			JsonObject userInfo = JsonGenerationHelpers.userDescription(cached.name()
					, cached.description()
					, _context.baseUrl + cached.userPicCid().toSafeString()
					, cached.emailOrNull()
					, cached.websiteOrNull()
			);
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
