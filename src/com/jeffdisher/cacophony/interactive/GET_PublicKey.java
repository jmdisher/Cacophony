package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the user's public key as a plain-text string.
 */
public class GET_PublicKey implements ValidatedEntryPoints.GET
{
	private final Context _context;
	
	public GET_PublicKey(Context context
	)
	{
		_context = context;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws Throwable
	{
		response.setContentType("text/plain");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().print(_context.publicKey.toPublicKey());
	}
}
