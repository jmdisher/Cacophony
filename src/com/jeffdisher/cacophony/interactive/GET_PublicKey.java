package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.types.IpfsKey;

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
		IpfsKey key = _context.getSelectedKey();
		if (null != key)
		{
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(key.toPublicKey());
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
}
