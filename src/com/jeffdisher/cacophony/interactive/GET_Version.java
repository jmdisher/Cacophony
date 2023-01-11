package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.logic.JsonGenerationHelpers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns build information as a JSON struct:
 * -hash - the git hash of the current build
 * -version - the human-readable version name of the current build
 */
public class GET_Version implements ValidatedEntryPoints.GET
{
	public GET_Version()
	{
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		JsonObject dataVersion = JsonGenerationHelpers.dataVersion();
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().print(dataVersion.toString());
	}
}
