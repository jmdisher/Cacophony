package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns a string with the processing command, but only if it can be changed by the user.  Otherwise, returns 403
 * forbidden.
 */
public class GET_ProcessingCommand implements ValidatedEntryPoints.GET
{
	private final String _processingCommand;
	private final boolean _canChangeCommand;
	
	public GET_ProcessingCommand(String processingCommand, boolean canChangeCommand)
	{
		_processingCommand = processingCommand;
		_canChangeCommand = canChangeCommand;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		if (_canChangeCommand)
		{
			// Return the command so they can edit it.
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(_processingCommand);
		}
		else
		{
			// We don't bother giving them the command since they can't change it, anyway.
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
	}
}
