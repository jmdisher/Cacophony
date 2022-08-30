package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import com.jeffdisher.breakwater.IPostRawHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class POST_Raw_Stop implements IPostRawHandler
{
	private final String _xsrf;
	private final CountDownLatch _stopLatch;
	
	public POST_Raw_Stop(String xsrf, CountDownLatch stopLatch)
	{
		_xsrf = xsrf;
		_stopLatch = stopLatch;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			response.setContentType("text/plain;charset=utf-8");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print("Shutting down...");
			_stopLatch.countDown();
		}
	}
}
