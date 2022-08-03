package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.util.resource.Resource;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.breakwater.IDeleteHandler;
import com.jeffdisher.breakwater.IGetHandler;
import com.jeffdisher.breakwater.IPostFormHandler;
import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.RestServer;
import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.data.local.v1.Draft;
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
		
		server.addGetHandler("/drafts", 0, new GetDraftsListHandler(manager));
		server.addPostRawHandler("/createDraft", 0, new CreateDraftHandler(manager));
		server.addGetHandler("/draft", 1, new GetDraftHandler(manager));
		server.addPostFormHandler("/draft", 1, new UpdateDraftTextHandler(manager));
		server.addDeleteHandler("/draft", 1, new DeleteDraftHandler(manager));
		server.addPostRawHandler("/draft/publish", 1, new PublishDraftHandler(environment, manager));
		
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

	/**
	 * Returns all the active drafts as an array of Draft types.
	 */
	private static class GetDraftsListHandler implements IGetHandler
	{
		private final DraftManager _draftManager;
		
		public GetDraftsListHandler(DraftManager draftManager)
		{
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			_verifySafeRequest(request);
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

	/**
	 * Creates a new draft with default storage state and returns its default state as a Draft type.
	 */
	private static class CreateDraftHandler implements IPostRawHandler
	{
		private final DraftManager _draftManager;
		
		public CreateDraftHandler(DraftManager draftManager)
		{
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			_verifySafeRequest(request);
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			
			// Generate an ID - should be random so just get some bits from the time.
			int id = Math.abs((int)(System.currentTimeMillis() >> 8L));
			Draft draft = InteractiveHelpers.createNewDraft(_draftManager, id);
			response.getWriter().print(draft.toJson().toString());
		}
	}

	/**
	 * Returns the given draft as a Draft type.
	 */
	private static class GetDraftHandler implements IGetHandler
	{
		private final DraftManager _draftManager;
		
		public GetDraftHandler(DraftManager draftManager)
		{
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] variables) throws IOException
		{
			_verifySafeRequest(request);
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

	/**
	 * Updates the given draft with the included data and returns 200 on success or 404 if not found.
	 */
	private static class UpdateDraftTextHandler implements IPostFormHandler
	{
		private final DraftManager _draftManager;
		
		public UpdateDraftTextHandler(DraftManager draftManager)
		{
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws IOException
		{
			_verifySafeRequest(request);
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

	/**
	 * Deletes the given draft and returns 200 on success or 404 if not found.
	 */
	private static class DeleteDraftHandler implements IDeleteHandler
	{
		private final DraftManager _draftManager;
		
		public DeleteDraftHandler(DraftManager draftManager)
		{
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			_verifySafeRequest(request);
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

	/**
	 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
	 */
	private static class PublishDraftHandler implements IPostRawHandler
	{
		private final IEnvironment _environment;
		private final DraftManager _draftManager;
		
		public PublishDraftHandler(IEnvironment environment, DraftManager draftManager)
		{
			_environment = environment;
			_draftManager = draftManager;
		}
		
		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
		{
			_verifySafeRequest(request);
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


	private static void _verifySafeRequest(HttpServletRequest request)
	{
		// CORS should stop remote connection attempts since the front-end hard-codes 127.0.0.1 but assert since it is a security concern.
		Assert.assertTrue("127.0.0.1".equals(request.getRemoteAddr()));
	}
}
