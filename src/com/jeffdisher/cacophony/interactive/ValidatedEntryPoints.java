package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * This class exists only as a layer above our REST entry-points to handle the common validation logic we want to apply
 * to almost all of them.
 * It checks the XSRF token, the IP address of the remote client, and also applies our standard responses to various
 * kinds of exceptions which may occur during an invocation.
 */
public class ValidatedEntryPoints
{
	private final RestServer _server;
	private final String _xsrf;

	public ValidatedEntryPoints(RestServer server, String xsrf)
	{
		_server = server;
		_xsrf = xsrf;
	}

	public void addGetHandler(String pathPrefix, int variableCount, GET handler)
	{
		_server.addGetHandler(pathPrefix, variableCount, new VerifiedGet(handler));
	}

	public void addPostRawHandler(String pathPrefix, int variableCount, POST_Raw handler)
	{
		_server.addPostRawHandler(pathPrefix, variableCount, new VerifiedPostRaw(handler));
	}

	public void addPostFormHandler(String pathPrefix, int variableCount, POST_Form handler)
	{
		_server.addPostFormHandler(pathPrefix, variableCount, new VerifiedPostForm(handler));
	}

	public void addDeleteHandler(String pathPrefix, int variableCount, DELETE handler)
	{
		_server.addDeleteHandler(pathPrefix, variableCount, new VerifiedDelete(handler));
	}


	private class VerifiedGet implements IGetHandler
	{
		private final GET _handler;
		public VerifiedGet(GET handler)
		{
			_handler = handler;
		}
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, variables);
			});
		}
	}

	private class VerifiedPostRaw implements IPostRawHandler
	{
		private final POST_Raw _handler;
		public VerifiedPostRaw(POST_Raw handler)
		{
			_handler = handler;
		}
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, variables);
			});
		}
	}

	private class VerifiedPostForm implements IPostFormHandler
	{
		private final POST_Form _handler;
		public VerifiedPostForm(POST_Form handler)
		{
			_handler = handler;
		}
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, pathVariables, formVariables);
			});
		}
	}

	private class VerifiedDelete implements IDeleteHandler
	{
		private final DELETE _handler;
		public VerifiedDelete(DELETE handler)
		{
			_handler = handler;
		}
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, variables);
			});
		}
	}

	private static void _commonChecks(String xsrf, HttpServletRequest request, HttpServletResponse response, ThrowingRunnable task) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(xsrf, request, response))
		{
			try
			{
				task.run();
			}
			catch (IpfsConnectionException e)
			{
				// Failures in contacting the IPFS server will just be considered an internal error, for now.
				// Note that some timeouts may also end up here but we ideally want to handle those in a different way.
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			catch (UsageException | VersionException e)
			{
				// Not expected after start-up.
				throw Assert.unexpected(e);
			}
			catch (IOException e)
			{
				// This could be an issue interacting with the parameters so we throw it back.
				throw e;
			}
			catch (Throwable t)
			{
				// This case is unknown and we would want to identify it and commute it into an error we can handle.
				t.printStackTrace();
				throw Assert.unexpected(t);
			}
		}
		else
		{
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
	}


	public interface GET
	{
		void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable;
	}

	public interface POST_Raw
	{
		void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable;
	}

	public interface POST_Form
	{
		void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable;
	}

	public interface DELETE
	{
		void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable;
	}

	private interface ThrowingRunnable
	{
		void run() throws Throwable;
	}
}
