package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns build information as a JSON struct:
 * -hash - the git hash of the current build
 * -version - the human-readable version name of the current build
 */
public class GET_Version implements IGetHandler
{
	private final String _xsrf;
	
	public GET_Version(String xsrf)
	{
		_xsrf = xsrf;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			JsonObject dataVersion = JsonGenerationHelpers.dataVersion();
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(dataVersion.toString());
		}
	}
}
