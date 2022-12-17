package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.IPostRawHandler;
import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.types.VersionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
 * Accepts 2 path arguments:
 * [0] = draft ID
 * [1] = PublishType
 */
public class POST_Raw_DraftPublish implements IPostRawHandler
{
	private final IEnvironment _environment;
	private final String _xsrf;
	private final BackgroundOperations _backgroundOperations;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftPublish(IEnvironment environment
			, String xsrf
			, BackgroundOperations backgroundOperations
			, DraftManager draftManager
	)
	{
		_environment = environment;
		_xsrf = xsrf;
		_backgroundOperations = backgroundOperations;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws IOException
	{
		if (InteractiveHelpers.verifySafeRequest(_xsrf, request, response))
		{
			// Make sure there isn't already a publish update in-progress (later, we can just overwrite it).
			_backgroundOperations.waitForPendingPublish();
			
			try (IWritingAccess access = StandardAccess.writeAccess(_environment))
			{
				int draftId = Integer.parseInt(pathVariables[0]);
				PublishType type = PublishType.valueOf(pathVariables[1]);
				
				IpfsFile newRoot = InteractiveHelpers.postExistingDraft(_environment
						, access
						, _draftManager
						, draftId
						, (PublishType.VIDEO == type)
						, (PublishType.AUDIO == type)
				);
				InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
				
				// The publish is something we can wait on, asynchronously, in a different call.
				FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
				_backgroundOperations.waitAndStorePublishOperation(asyncPublish);
				
				response.setStatus(HttpServletResponse.SC_OK);
			}
			catch (NumberFormatException e)
			{
				// Draft ID invalid.
				response.getWriter().println("Invalid draft ID: \"" + pathVariables[0] + "\"");
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			catch (IllegalArgumentException e)
			{
				// Typically thrown by PublishType.valueOf.
				response.getWriter().println("Invalid draft type: \"" + pathVariables[1] + "\"");
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			catch (FileNotFoundException e)
			{
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
			catch (IpfsConnectionException e)
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				e.printStackTrace(response.getWriter());
			}
			catch (UsageException | VersionException e)
			{
				// Not expected after start-up.
				throw Assert.unexpected(e);
			}
		}
	}


	/**
	 * This type is just used to formalize the interfact but isn't part of the actual data model.
	 */
	private static enum PublishType
	{
		VIDEO,
		TEXT_ONLY,
		AUDIO,
	}
}
