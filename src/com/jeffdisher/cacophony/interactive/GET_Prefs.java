package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LocalConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the owning user's preferences as a JSON struct:
 * -edgeSize (int)
 * -followerCacheBytes (long)
 */
public class GET_Prefs implements IGetHandler
{
	private final String _xsrf;
	private final LocalConfig _localConfig;
	
	public GET_Prefs(String xsrf, LocalConfig localConfig)
	{
		_xsrf = xsrf;
		_localConfig = localConfig;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			GlobalPrefs prefs = _localConfig.readSharedPrefs();
			JsonObject userInfo = JsonGenerationHelpers.prefs(prefs);
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
	}
}
