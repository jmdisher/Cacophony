package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.core.CloseStatus;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostHandler;
import com.jeffdisher.breakwater.IWebSocketFactory;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.data.local.v1.Draft;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.utils.Assert;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class InteractiveServer
{
	public static void runServerUntilStop(DraftManager manager, Resource staticResource, int port)
	{
		CountDownLatch stopLatch = new CountDownLatch(1);
		RestServer server = new RestServer(port, staticResource);
		server.addWebSocketFactory("/draft/saveVideo", 3, "webm", new IWebSocketFactory() {
			// Opens a video save web socket for the given draft ID.
			@Override
			public WebSocketListener create(String[] variables)
			{
				int draftId = Integer.parseInt(variables[0]);
				int height = Integer.parseInt(variables[1]);
				int width = Integer.parseInt(variables[2]);
				WebSocketListener ws = new WebSocketListener()
				{
					private VideoSaver _saver;
					@Override
					public void onWebSocketClose(int statusCode, String reason)
					{
						InteractiveHelpers.closeNewVideo(_saver, "video/webm", height, width);
						_saver = null;
					}
					@Override
					public void onWebSocketConnect(Session session)
					{
						// 256 KiB should be reasonable.
						session.setMaxBinaryMessageSize(256 * 1024);
						Assert.assertTrue(null == _saver);
						try
						{
							_saver = InteractiveHelpers.openNewVideo(manager, draftId);
						}
						catch (FileNotFoundException e)
						{
							// This happens in the case where the draft doesn't exist.
							session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
						}
					}
					@Override
					public void onWebSocketBinary(byte[] payload, int offset, int len)
					{
						Assert.assertTrue(null != _saver);
						InteractiveHelpers.appendToNewVideo(_saver, payload, offset, len);
					}
				};
				return ws;
			}});
		server.addWebSocketFactory("/draft/processVideo", 2, "process", new IWebSocketFactory() {
			// Opens a video processing web socket for the given draft ID and command.
			@Override
			public WebSocketListener create(String[] variables)
			{
				int draftId = Integer.parseInt(variables[0]);
				String processCommand = variables[1];
				WebSocketListener ws = new WebSocketListener()
				{
					private VideoProcessor _processor;
					@Override
					public void onWebSocketClose(int statusCode, String reason)
					{
						InteractiveHelpers.closeVideoProcessor(_processor);
						_processor = null;
					}
					@Override
					public void onWebSocketConnect(Session session)
					{
						Assert.assertTrue(null == _processor);
						try
						{
							_processor = InteractiveHelpers.openVideoProcessor(new VideoProcessor.ProcessWriter()
							{
								@Override
								public void totalBytesProcessed(long bytesProcessed)
								{
									JsonObject object = new JsonObject();
									object.add("type", "progress");
									object.add("bytes", bytesProcessed);
									try
									{
										session.getRemote().sendString(object.toString());
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
										session.getRemote().sendString(object.toString());
									}
									catch (IOException e)
									{
										// Not yet sure why this may happen (race on close?).
										throw Assert.unexpected(e);
									}
								}
								@Override
								public void processingDone()
								{
									JsonObject object = new JsonObject();
									object.add("type", "done");
									try
									{
										session.getRemote().sendString(object.toString());
									}
									catch (IOException e)
									{
										// Not yet sure why this may happen (race on close?).
										throw Assert.unexpected(e);
									}
								}
							}, manager, draftId, processCommand);
						}
						catch (FileNotFoundException e)
						{
							// This happens in the case where the draft doesn't exist.
							session.close(CloseStatus.SERVER_ERROR, "Draft does not exist");
						}
						catch (IOException e)
						{
							// This happened if we failed to run the processor.
							session.close(CloseStatus.SERVER_ERROR, "Failed to run processing program: \"" + processCommand + "\"");
						}
					}
				};
				return ws;
			}});
		server.addGetHandler("/drafts", 0, new IGetHandler() {
			// Returns all the active drafts as an array of Draft types.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				
				// We need to list all the drafts we have.  This is an array of draft types.
				JsonArray draftArray = new JsonArray();
				for (Draft draft : InteractiveHelpers.listDrafts(manager))
				{
					JsonObject serialized = draft.toJson();
					draftArray.add(serialized);
				}
				response.getWriter().print(draftArray.toString());
			}});
		server.addPostHandler("/createDraft", 0, new IPostHandler() {
			// Creates a new draft with default storage state and returns its default state as a Draft type.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException
			{
				response.setContentType("application/json");
				response.setStatus(HttpServletResponse.SC_OK);
				
				// Generate an ID - should be random so just get some bits from the time.
				int id = Math.abs((int)(System.currentTimeMillis() >> 8L));
				Draft draft = InteractiveHelpers.createNewDraft(manager, id);
				response.getWriter().print(draft.toJson().toString());
			}});
		server.addGetHandler("/draft", 1, new IGetHandler() {
			// Returns the given draft as a Draft type.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					Draft draft = InteractiveHelpers.readExistingDraft(manager, draftId);
					
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					response.getWriter().print(draft.toJson().toString());
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addPostHandler("/draft", 1, new IPostHandler() {
			// Updates the given draft with the included data and returns 200 on success or 404 if not found.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				String title = formVariables.getIfSingle("title");
				String description = formVariables.getIfSingle("description");
				if ((null != title) && !title.isEmpty() && (null != description))
				{
					try
					{
						InteractiveHelpers.updateDraftTitle(manager, draftId, title, description);
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
			}});
		server.addDeleteHandler("/draft", 1, new IDeleteHandler() {
			// Deletes the given draft and returns 200 on success or 404 if not found.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				
				try
				{
					InteractiveHelpers.deleteExistingDraft(manager, draftId);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addPostHandler("/draft/publish", 1, new IPostHandler() {
			// Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				try
				{
					InteractiveHelpers.publishExistingDraft(manager, draftId);
					response.setStatus(HttpServletResponse.SC_OK);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addGetHandler("/draft/thumb", 1, new IGetHandler() {
			// Returns the thumbnail for this draft as a JPEG.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.loadThumbnailToStream(manager, draftId, (String mime) -> {
						// This is called only on success.
						response.setContentType(mime);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addPostHandler("/draft/thumb", 1, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				
				try
				{
					InputStream input = request.getInputStream();
					InteractiveHelpers.saveThumbnailFromStream(manager, draftId, input);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addGetHandler("/draft/originalVideo", 1, new IGetHandler() {
			// Returns the original video for this draft as a WEBM stream.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.writeOriginalVideoToStream(manager, draftId, (String mime) -> {
						// Called only when the video is found.
						response.setContentType(mime);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addGetHandler("/draft/processedVideo", 1, new IGetHandler() {
			// Returns the processed video for this draft as a WEBM stream.
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException {
				int draftId = Integer.parseInt(variables[0]);
				try
				{
					ServletOutputStream output = response.getOutputStream();
					InteractiveHelpers.writeProcessedVideoToStream(manager, draftId, (String mime) -> {
						// Called only when the video is found.
						response.setContentType(mime);
						response.setStatus(HttpServletResponse.SC_OK);
					}, output);
				}
				catch (FileNotFoundException e)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}});
		server.addPostHandler("/stop", 0, new IPostHandler() {
			@Override
			public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables, StringMultiMap<byte[]> multiPart, byte[] rawPost) throws IOException
			{
				response.setContentType("text/plain;charset=utf-8");
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Shutting down...");
				stopLatch.countDown();
			}});
		server.start();
		System.out.println("Cacophony interactive server running: http://localhost:" + port);
		
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
}
