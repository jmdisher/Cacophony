package com.jeffdisher.cacophony.interactive;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	public static void runServerUntilStop(IEnvironment environment, DraftManager manager, Resource staticResource, int port)
	{
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(port, staticResource);
		server.addPostRawHandler("/stop", 0, new StopHandler(stopLatch));
		server.start();
		System.out.println("Cacophony interactive server running: http://127.0.0.1:" + port);
		
		try
		{
			stopLatch.await();
		}
		catch (InterruptedException e)
		{
			// This thread isn't interrupted.
			throw Assert.unexpected(e);
		}
		server.stop();
	}


	private static class StopHandler implements IPostRawHandler
	{
		private final CountDownLatch _stopLatch;
		
		public StopHandler(CountDownLatch stopLatch)
		{
			_stopLatch = stopLatch;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			_verifySafeRequest(request);
			response.setContentType("text/plain;charset=utf-8");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().print("Shutting down...");
			_stopLatch.countDown();
		}
	}


	private static void _verifySafeRequest(HttpServletRequest request)
	{
		// CORS should stop remote connection attempts since the front-end hard-codes 127.0.0.1 but assert since it is a security concern.
		Assert.assertTrue("127.0.0.1".equals(request.getRemoteAddr()));
	}
}
