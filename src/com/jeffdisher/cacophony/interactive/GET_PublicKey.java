package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the user's public key as a plain-text string.
 */
public class GET_PublicKey implements ValidatedEntryPoints.GET
{
	private final ICommand.Context _context;
	
	public GET_PublicKey(ICommand.Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		try (IReadingAccess access = StandardAccess.readAccess(_context.environment, _context.logger))
		{
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(access.getPublicKey().toPublicKey());
		}
	}
}
