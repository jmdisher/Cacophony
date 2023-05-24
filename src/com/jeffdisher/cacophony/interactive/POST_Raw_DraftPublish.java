package com.jeffdisher.cacophony.interactive;

import java.io.FileNotFoundException;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Publishes the given draft and returns 200 on success, 404 if not found, or 500 if something went wrong.
 * Accepts 2 path arguments:
 * [0] = draft ID
 * [1] = PublishType
 */
public class POST_Raw_DraftPublish implements ValidatedEntryPoints.POST_Raw
{
	private final Context _context;
	private final BackgroundOperations _backgroundOperations;
	private final DraftManager _draftManager;
	
	public POST_Raw_DraftPublish(Context context
			, BackgroundOperations backgroundOperations
			, DraftManager draftManager
	)
	{
		_context = context;
		_backgroundOperations = backgroundOperations;
		_draftManager = draftManager;
	}
	
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables) throws Throwable
	{
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		// We will try to override with the given public key, which will cause an exception on StandardAccess if it isn't really a home key.
		Context thisContext = _context.cloneWithSelectedKey(homePublicKey);
		try (IWritingAccess access = StandardAccess.writeAccess(thisContext))
		{
			int draftId = Integer.parseInt(pathVariables[1]);
			PublishType type = PublishType.valueOf(pathVariables[2]);
			
			PublishHelpers.PublishResult result = InteractiveHelpers.postExistingDraft(thisContext.logger
					, access
					, _draftManager
					, draftId
					, (PublishType.VIDEO == type)
					, (PublishType.AUDIO == type)
					, thisContext.getSelectedKey()
			);
			InteractiveHelpers.deleteExistingDraft(_draftManager, draftId);
			
			// The publish is something we can wait on, asynchronously, in a different call.
			_backgroundOperations.requestPublish(thisContext.getSelectedKey(), result.newIndexRoot());
			IpfsFile newElement = result.newRecordCid();
			thisContext.entryRegistry.addLocalElement(thisContext.getSelectedKey(), newElement);
			
			// We are going to re-read this element from the network in order to update the LocalRecordCache.  This is a
			// bit odd, since we could have updated this during the publish operation, but that would have required some
			// very specialized plumbing.  Additionally, this allows us to use the existing initialization code and
			// verify that the assumptions around this are consistent.
			// In the future, we may want to refactor this so that it can be more elegantly updated as the network read
			// seems wrong.
			LocalRecordCacheBuilder.updateCacheWithNewUserPost(access, thisContext.recordCache, newElement);
			
			response.setStatus(HttpServletResponse.SC_OK);
		}
		catch (NumberFormatException e)
		{
			// Draft ID invalid.
			response.getWriter().println("Invalid draft ID: \"" + pathVariables[1] + "\"");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		catch (IllegalArgumentException e)
		{
			// Typically thrown by PublishType.valueOf.
			response.getWriter().println("Invalid draft type: \"" + pathVariables[2] + "\"");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		catch (FileNotFoundException e)
		{
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
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
