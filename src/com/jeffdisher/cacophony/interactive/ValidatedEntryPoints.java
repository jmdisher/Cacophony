package com.jeffdisher.cacophony.interactive;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;

import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;

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

	/**
	 * Creates the entry-point wrapper over top of the given server, validating against the given xsrf.
	 * 
	 * @param server The server these entry-points should be built on.
	 * @param xsrf The XSRF token to use in validation.
	 */
	public ValidatedEntryPoints(RestServer server, String xsrf)
	{
		_server = server;
		_xsrf = xsrf;
	}

	/**
	 * Installs a validated GET handler.
	 * 
	 * @param path The path to bind.
	 * @param handler The handler to install.
	 */
	public void addGetHandler(String path, GET handler)
	{
		_server.addGetHandler(path, new VerifiedGet(handler));
	}

	/**
	 * Installs a validated raw POST handler.
	 * 
	 * @param path The path to bind.
	 * @param handler The handler to install.
	 */
	public void addPostRawHandler(String path, POST_Raw handler)
	{
		_server.addPostRawHandler(path, new VerifiedPostRaw(handler));
	}

	/**
	 * Installs a validated form POST handler.
	 * 
	 * @param path The path to bind.
	 * @param handler The handler to install.
	 */
	public void addPostFormHandler(String path, POST_Form handler)
	{
		_server.addPostFormHandler(path, new VerifiedPostForm(handler));
	}

	/**
	 * Installs a validated DELETE handler.
	 * 
	 * @param path The path to bind.
	 * @param handler The handler to install.
	 */
	public void addDeleteHandler(String path, DELETE handler)
	{
		_server.addDeleteHandler(path, new VerifiedDelete(handler));
	}

	/**
	 * Installs a validated WebSocket factory.
	 * 
	 * @param path The path to bind.
	 * @param protocolName The protocol to bind.
	 * @param factory The factory to install.
	 */
	public void addWebSocketFactory(String path, String protocolName, WEB_SOCKET_FACTORY factory)
	{
		_server.addWebSocketFactory(path, protocolName, new VerifiedSocketFactory(factory));
	}


	private class VerifiedGet implements IGetHandler
	{
		private final GET _handler;
		public VerifiedGet(GET handler)
		{
			_handler = handler;
		}
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, path);
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
		public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, path);
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
		public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, path, formVariables);
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
		public void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws IOException
		{
			_commonChecks(_xsrf, request, response, () -> {
				_handler.handle(request, response, path);
			});
		}
	}

	private class VerifiedSocketFactory implements IWebSocketFactory
	{
		private final WEB_SOCKET_FACTORY _handler;
		public VerifiedSocketFactory(WEB_SOCKET_FACTORY handler)
		{
			_handler = handler;
		}
		@Override
		public WebSocketListener create(JettyServerUpgradeRequest upgradeRequest, Object[] path)
		{
			return InteractiveHelpers.verifySafeWebSocket(_xsrf, upgradeRequest)
					? _handler.build(path)
					: null
			;
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
			catch (UsageException e)
			{
				// If it common to see usage exceptions thrown for things like missing parameters so handle that here.
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			catch (IpfsConnectionException e)
			{
				// Failures in contacting the IPFS server will just be considered an internal error, for now.
				// Note that some timeouts may also end up here but we ideally want to handle those in a different way.
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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


	/**
	 * Validated GET handler.
	 */
	public interface GET
	{
		/**
		 * Handles the call, after validation.
		 * 
		 * @param request The request.
		 * @param response The response.
		 * @param path The processed path components.
		 * @throws Throwable If something went wrong.
		 */
		void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable;
	}

	/**
	 * Validated raw POST handler.
	 */
	public interface POST_Raw
	{
		/**
		 * Handles the call, after validation.
		 * 
		 * @param request The request.
		 * @param response The response.
		 * @param path The processed path components.
		 * @throws Throwable If something went wrong.
		 */
		void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable;
	}

	/**
	 * Validated form POST handler.
	 */
	public interface POST_Form
	{
		/**
		 * Handles the call, after validation.
		 * 
		 * @param request The request.
		 * @param response The response.
		 * @param path The processed path components.
		 * @param formVariables The data posted as form-encoded.
		 * @throws Throwable If something went wrong.
		 */
		void handle(HttpServletRequest request, HttpServletResponse response, Object[] path, StringMultiMap<String> formVariables) throws Throwable;
	}

	/**
	 * Validated DELETE handler.
	 */
	public interface DELETE
	{
		/**
		 * Handles the call, after validation.
		 * 
		 * @param request The request.
		 * @param response The response.
		 * @param path The processed path components.
		 * @throws Throwable If something went wrong.
		 */
		void handle(HttpServletRequest request, HttpServletResponse response, Object[] path) throws Throwable;
	}

	/**
	 * Validated WebSocket factory.
	 */
	public interface WEB_SOCKET_FACTORY
	{
		/**
		 * Builds the WebSocketListener, after validation.
		 * 
		 * @param path The processed path components.
		 * @return The listener for this WebSocket connection.
		 */
		WebSocketListener build(Object[] path);
	}

	private interface ThrowingRunnable
	{
		void run() throws Throwable;
	}
}
