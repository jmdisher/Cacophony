package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Helpers called by the RunCommand to setup and then handle the web requests.
 */
public class InteractiveServer
{
	private static final String XSRF = "XSRF";

	public static void runServerUntilStop(IEnvironment environment, DraftManager manager, Resource staticResource, int port, String forcedCommand)
	{
		String xsrf = "XSRF_TOKEN_" + Math.random();
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(port, staticResource);
		server.addPostRawHandler("/cookie", 0, new PostSetCookieHandler(xsrf));
		server.addPostRawHandler("/stop", 0, new StopHandler(xsrf, stopLatch));
		
		server.addGetHandler("/drafts", 0, new GetDraftsListHandler(xsrf, manager));
		server.addPostRawHandler("/createDraft", 0, new CreateDraftHandler(xsrf, manager));
		server.addGetHandler("/draft", 1, new GetDraftHandler(xsrf, manager));
		server.addPostFormHandler("/draft", 1, new UpdateDraftTextHandler(xsrf, manager));
		server.addDeleteHandler("/draft", 1, new DeleteDraftHandler(xsrf, manager));
		server.addPostRawHandler("/draft/publish", 1, new PublishDraftHandler(environment, xsrf, manager));
		
		server.addGetHandler("/draft/thumb", 1, new GetThumbnailImageHandler(xsrf, manager));
		server.addPostRawHandler("/draft/thumb", 3, new PostThumbnailImageHandler(xsrf, manager));
		
		server.addGetHandler("/draft/originalVideo", 1, new GetOriginalVideoHandler(xsrf, manager));
		server.addGetHandler("/draft/processedVideo", 1, new GetProcessedVideoHandler(xsrf, manager));
		
		server.addWebSocketFactory("/draft/saveVideo", 3, "webm", new SaveOriginalVideoSocketFactory(xsrf, manager));
		server.addWebSocketFactory("/draft/processVideo", 2, "process", new ProcessVideoSocketFactory(xsrf, manager, forcedCommand));
		
		server.start();
		System.out.println("Cacophony interactive server running: http://127.0.0.1:" + port);
		if (null != forcedCommand)
		{
			System.out.println("Forced processing command: \"" + forcedCommand + "\"");
		}
		else
		{
			System.out.println("WARNING:  Dangerous processing mode enabled!  User will be able to control server-side command from front-end.");
		}
		
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
		private final String _xsrf;
		private final CountDownLatch _stopLatch;
		
		public StopHandler(String xsrf, CountDownLatch stopLatch)
		{
			_xsrf = xsrf;
			_stopLatch = stopLatch;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Shutting down...");
				_stopLatch.countDown();
			}
		}
	}

	/**
	 * Returns all the active drafts as an array of Draft types.
	 */
	private static class GetDraftsListHandler implements IGetHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public GetDraftsListHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				
				// We need to list all the drafts we have.  This is an array of draft types.
				JsonArray draftArray = new JsonArray();
				for (Draft draft : InteractiveHelpers.listDrafts(_draftManager))
				{
					JsonObject serialized = draft.toJson();
					draftArray.add(serialized);
				}
				response.getWriter().print(draftArray.toString());
			}
		}
	}

	private static class PostSetCookieHandler implements IPostRawHandler
	{
		private final String _xsrf;
		
		public PostSetCookieHandler(String xsrf)
		{
			_xsrf = xsrf;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if ("127.0.0.1".equals(request.getRemoteAddr()))
			{
				Cookie cookie = new Cookie("XSRF", _xsrf);
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

	/**
	 * Creates a new draft with default storage state and returns its default state as a Draft type.
	 */
	private static class CreateDraftHandler implements IPostRawHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public CreateDraftHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				
				// Generate an ID - should be random so just get some bits from the time.
				int id = Math.abs((int)(System.currentTimeMillis() >> 8L));
				Draft draft = InteractiveHelpers.createNewDraft(_draftManager, id);
				response.getWriter().print(draft.toJson().toString());
			}
		}
	}

	/**
	 * Returns the given draft as a Draft type.
	 */
	private static class GetDraftHandler implements IGetHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public GetDraftHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					Draft draft = InteractiveHelpers.readExistingDraft(_draftManager, draftId);
					
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(draft.toJson().toString());
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Updates the given draft with the included data and returns 200 on success or 404 if not found.
	 */
	private static class UpdateDraftTextHandler implements IPostFormHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public UpdateDraftTextHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				String title = formVariables.getIfSingle("title");
				String description = formVariables.getIfSingle("description");
				// Note that the discussion URL can be null - empty strings should be made null.
				String discussionUrl = formVariables.getIfSingle("discussionUrl");
				if ((null != discussionUrl) && discussionUrl.isEmpty())
				{
					discussionUrl = null;
				}
				if ((null != title) && !title.isEmpty() && (null != description))
				{
					try
					{
						InteractiveHelpers.updateDraftText(_draftManager, draftId, title, description, discussionUrl);
						response.setStatus(HttpServletResponse.SC_OK);
					}
					catch (FileNotFoundException e)
					{
						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
					}
				}
				else
				{
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				}
			}
		}
	}

	/**
	 * Deletes the given draft and returns 200 on success or 404 if not found.
	 */
	private static class DeleteDraftHandler implements IDeleteHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public DeleteDraftHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				
				try
				{
					InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
	 */
	private static class PublishDraftHandler implements IPostRawHandler
	{
		private final IEnvironment _environment;
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public PublishDraftHandler(IEnvironment environment, String xsrf, DraftManager draftManager)
		{
			_environment = environment;
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				try
				{
					InteractiveHelpers.publishExistingDraft(_environment, _draftManager, draftId);
					InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Returns the thumbnail for this draft as a JPEG.
	 */
	private static class GetThumbnailImageHandler implements IGetHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public GetThumbnailImageHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.loadThumbnailToStream(_draftManager, draftId, (String mime) -> {
						// This is called only on success.
						response.setContentType(mime);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	private static class PostThumbnailImageHandler implements IPostRawHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public PostThumbnailImageHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				int height = Integer.parseInt(pathVariables[1]);
				int width = Integer.parseInt(pathVariables[2]);
				
				try
				{
					InputStream input = request.getInputStream();
					InteractiveHelpers.saveThumbnailFromStream(_draftManager, draftId, height, width, input);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Returns the original video for this draft as a WEBM stream.
	 */
	private static class GetOriginalVideoHandler implements IGetHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public GetOriginalVideoHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.writeOriginalVideoToStream(_draftManager, draftId, (String mime, Long byteSize) -> {
						// Called only when the video is found.
						response.setContentType(mime);
						response.setContentLengthLong(byteSize);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Returns the processed video for this draft as a WEBM stream.
	 */
	private static class GetProcessedVideoHandler implements IGetHandler
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public GetProcessedVideoHandler(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			if (_verifySafeRequest(_xsrf, request, response))
			{
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.writeProcessedVideoToStream(_draftManager, draftId, (String mime, Long byteSize) -> {
						// Called only when the video is found.
						response.setContentType(mime);
						response.setContentLengthLong(byteSize);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
	}

	/**
	 * Opens a video save web socket for the given draft ID.
	 */
	private static class SaveOriginalVideoSocketFactory implements IWebSocketFactory
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		
		public SaveOriginalVideoSocketFactory(String xsrf, DraftManager draftManager)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
		}
		
		@Override
		public WebSocketListener create(String[] variables)
		{
			int draftId = Integer.parseInt(variables[0]);
			int height = Integer.parseInt(variables[1]);
			int width = Integer.parseInt(variables[2]);
			return new SaveVideoWebSocketListener(_xsrf, _draftManager, draftId, height, width);
		}
	}

	/**
	 * Opens a video processing web socket for the given draft ID and command.
	 */
	private static class ProcessVideoSocketFactory implements IWebSocketFactory
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final String _forcedCommand;
		
		public ProcessVideoSocketFactory(String xsrf, DraftManager draftManager, String forcedCommand)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_forcedCommand = forcedCommand;
		}
		
		@Override
		public WebSocketListener create(String[] variables)
		{
			int draftId = Integer.parseInt(variables[0]);
			String processCommand = variables[1];
			// See if we are supposed to override this connection.
			if (null != _forcedCommand)
			{
				processCommand = _forcedCommand;
			}
			System.out.println("Opening processing socket with local command: \"" + processCommand + "\"");
			return new ProcessVideoWebSocketListener(_xsrf, _draftManager, draftId, processCommand);
		}
	}

	private static class SaveVideoWebSocketListener implements WebSocketListener
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final int _draftId;
		private final int _height;
		private final int _width;
		private VideoSaver _saver;
		
		public SaveVideoWebSocketListener(String xsrf, DraftManager draftManager, int draftId, int height, int width)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_draftId = draftId;
			_height = height;
			_width = width;
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			InteractiveHelpers.closeNewVideo(_saver, "video/webm", _height, _width);
			_saver = null;
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (_verifySafeWebSocket(_xsrf, session))
			{
				// 256 KiB should be reasonable.
				session.setMaxBinaryMessageSize(256 * 1024);
				Assert.assertTrue(null == _saver);
				try
				{
					_saver = InteractiveHelpers.openNewVideo(_draftManager, _draftId);
				}
				catch (FileNotFoundException e)
				{
					// This happens in the case where the draft doesn't exist.
					session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
				}
			}
		}
		
		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len)
		{
			Assert.assertTrue(null != _saver);
			InteractiveHelpers.appendToNewVideo(_saver, payload, offset, len);
		}
	}

	private static class ProcessVideoWebSocketListener implements WebSocketListener
	{
		private final String _xsrf;
		private final DraftManager _draftManager;
		private final int _draftId;
		private final String _processCommand;
		private VideoProcessor _processor;
		
		public ProcessVideoWebSocketListener(String xsrf, DraftManager draftManager, int draftId, String processCommand)
		{
			_xsrf = xsrf;
			_draftManager = draftManager;
			_draftId = draftId;
			_processCommand = processCommand;
		}
		
		@Override
		public void onWebSocketClose(int statusCode, String reason)
		{
			InteractiveHelpers.closeVideoProcessor(_processor);
			_processor = null;
		}
		
		@Override
		public void onWebSocketConnect(Session session)
		{
			if (_verifySafeWebSocket(_xsrf, session))
			{
				Assert.assertTrue(null == _processor);
				try
				{
					_processor = InteractiveHelpers.openVideoProcessor(new ProcessorCallbackHandler(session), _draftManager, _draftId, _processCommand);
				}
				catch (FileNotFoundException e)
				{
					// This happens in the case where the draft doesn't exist.
					session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
				}
				catch (IOException e)
				{
					// This happened if we failed to run the processor.
					session.close(CloseStatus.SERVER_ERROR, "Failed to run processing program: \"" + _processCommand + "\"");
				}
			}
		}
	}

	private static class ProcessorCallbackHandler implements VideoProcessor.ProcessWriter
	{
		private final Session _session;
		
		public ProcessorCallbackHandler(Session session)
		{
			_session = session;
		}
		
		@Override
		public void totalBytesProcessed(long bytesProcessed)
		{
			JsonObject object = new JsonObject();
			object.add("type", "progress");
			object.add("bytes", bytesProcessed);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// Not yet sure why this may happen (race on close?).
				throw Assert.unexpected(e);
			}
		}
		
		@Override
		public void processingError(String error)
		{
			JsonObject object = new JsonObject();
			object.add("type", "error");
			object.add("string", error);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// Not yet sure why this may happen (race on close?).
				throw Assert.unexpected(e);
			}
		}
		
		@Override
		public void processingDone(long outputSizeBytes)
		{
			JsonObject object = new JsonObject();
			object.add("type", "done");
			object.add("bytes", outputSizeBytes);
			try
			{
				_session.getRemote().sendString(object.toString());
			}
			catch (IOException e)
			{
				// Not yet sure why this may happen (race on close?).
				throw Assert.unexpected(e);
			}
			_session.close();
			System.out.println("PROCESSING DONE");
		}
	}


	private static boolean _verifySafeRequest(String xsrf, HttpServletRequest request, HttpServletResponse response)
	{
		boolean isSafe = false;
		if ("127.0.0.1".equals(request.getRemoteAddr()))
		{
			String value = null;
			Cookie[] cookies = request.getCookies();
			if (null != cookies)
			{
				for (Cookie cookie : cookies)
				{
					if (XSRF.equals(cookie.getName()))
					{
						value = cookie.getValue();
					}
				}
			}
			if (xsrf.equals(value))
			{
				// This means all checks passed.
				isSafe = true;
			}
			else
			{
				isSafe = false;
				System.err.println("Invalid XSRF: \"" + value + "\"");
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			}
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid IP: " + request.getRemoteAddr());
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		}
		return isSafe;
	}

	private static boolean _verifySafeWebSocket(String xsrf, Session session)
	{
		boolean isSafe = false;
		String rawDescription = session.getRemoteAddress().toString();
		// This rawDescription looks like "/127.0.0.1:65657" so we need to parse it.
		String ip = rawDescription.substring(1).split(":")[0];
		if ("127.0.0.1".equals(ip))
		{
			String value = session.getUpgradeRequest().getCookies().stream().filter((cookie) -> XSRF.equals(cookie.getName())).map((cookie) -> cookie.getValue()).findFirst().get();
			if (xsrf.equals(value))
			{
				// This means all checks passed.
				isSafe = true;
			}
			else
			{
				isSafe = false;
				System.err.println("Invalid XSRF: \"" + value + "\"");
				session.close(CloseStatus.SERVER_ERROR, "Invalid XSRF");
			}
		}
		else
		{
			isSafe = false;
			System.err.println("Invalid IP: " + ip);
			session.close(CloseStatus.SERVER_ERROR, "Invalid IP");
		}
		return isSafe;
	}
}