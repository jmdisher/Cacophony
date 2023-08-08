package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import org.eclipse.jetty.http.HttpCookie;

import com.jeffdisher.breakwater.IPostRawHandler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class POST_Raw_Cookie implements IPostRawHandler
{
	private final String _xsrf;
	
	public POST_Raw_Cookie(String xsrf)
	{
		_xsrf = xsrf;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
	{
		if ("127.0.0.1".equals(request.getRemoteAddr()))
		{
			Cookie cookie = new Cookie("XSRF", _xsrf);
			cookie.setPath("/");
			cookie.setHttpOnly(true);
			cookie.setComment(HttpCookie.SAME_SITE_STRICT_COMMENT);
			response.addCookie(cookie);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		else
		{
			System.err.println("Invalid IP requesting XSRF token: " + request.getRemoteAddr());
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
	}
}
