package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Returns the user's public key as a plain-text string.
 */
public class GET_PublicKey implements IGetHandler
{
	private final String _xsrf;
	private final IpfsKey _publicKey;
	
	public GET_PublicKey(String xsrf, IpfsKey publicKey)
	{
		_xsrf = xsrf;
		_publicKey = publicKey;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print(_publicKey.toPublicKey());
		}
	}
}
