package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;
import com.jeffdisher.cacophony.logic.LocalConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a list of recommended public keys for this user, as a JSON array.
 */
public class GET_FolloweeKeys implements IGetHandler
{
	private final String _xsrf;
	private final LocalConfig _localConfig;
	
	public GET_FolloweeKeys(String xsrf, LocalConfig localConfig)
	{
		_xsrf = xsrf;
		_localConfig = localConfig;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			IReadOnlyLocalData data = _localConfig.getSharedLocalData().openForRead();
			FollowIndex followIndex = data.readFollowIndex();
			data.close();
			JsonArray keys = JsonGenerationHelpers.followeeKeys(followIndex);
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
	}
}
