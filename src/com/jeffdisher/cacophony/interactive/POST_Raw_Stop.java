package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Stops the server.
 */
public class POST_Raw_Stop implements ValidatedEntryPoints.POST_Raw
{
	private final CountDownLatch _stopLatch;
	
	public POST_Raw_Stop(CountDownLatch stopLatch)
	{
		_stopLatch = stopLatch;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/plain;charset=utf-8");
		response.getWriter().print("Shutting down...");
		_stopLatch.countDown();
	}
}
