package com.jeffdisher.cacophony.interactive;

import java.util.List;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.EditPostCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.types.IpfsFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Used to update an existing post from the local user's stream.
 * NOTE:  This "edit" support is very minimal, as it doesn't support changes to any of the attachments, only the title,
 * description, and discussionURL.
 * The path variable is the hash of the element to change.  Other data is passed as POST form:
 * -"NAME" - The name (can be empty).
 * -"DESCRIPTION" - The description (can be empty).
 * -"DISCUSSION_URL" - The discussion URL (if this is empty, we will interpret it as "no discussion").
 */
public class POST_Form_EditPost implements ValidatedEntryPoints.POST_Form
{
	public static final String VAR_NAME = "NAME";
	public static final String VAR_DESCRIPTION = "DESCRIPTION";
	public static final String VAR_DISCUSSION_URL = "DISCUSSION_URL";

	private final ICommand.Context _context;
	private final BackgroundOperations _background;

	public POST_Form_EditPost(ICommand.Context context
			, BackgroundOperations background
	)
	{
		_context = context;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		IpfsFile eltCid = IpfsFile.fromIpfsCid(pathVariables[0]);
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String discussionUrl = formVariables.getIfSingle(VAR_DISCUSSION_URL);
		
		EditPostCommand command = new EditPostCommand(eltCid, name, description, discussionUrl);
		OnePost result = InteractiveHelpers.runCommandAndHandleErrors(response
				, _context
				, command
		);
		if (null != result)
		{
			_handleCorrectCase(response, eltCid, result);
			
			// Output the new element CID.
			response.setContentType("text/plain");
			response.getWriter().print(result.recordCid.toSafeString());
		}
	}

	private void _handleCorrectCase(HttpServletResponse response, IpfsFile eltCid, OnePost result)
	{
		// Delete the old entry and add the new one.
		_context.entryRegistry.removeLocalElement(eltCid);
		IpfsFile newEltCid = result.recordCid;
		_context.entryRegistry.addLocalElement(newEltCid);
		
		// Account for the change of the CID in the record cache.  Even though we don't change the leaf
		// data, we still need to technically "move" them to the new record CID.
		StreamRecord record = result.streamRecord;
		List<DataElement> unchangedLeaves = record.getElements().getElement();
		for (DataElement leaf : unchangedLeaves)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (ElementSpecialType.IMAGE == leaf.getSpecial())
			{
				// This is the thumbnail.
				_context.recordCache.recordThumbnailReleased(eltCid, leafCid);
			}
			else if (leaf.getMime().startsWith("video/"))
			{
				int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
				_context.recordCache.recordVideoReleased(eltCid, leafCid, maxEdge);
			}
			else if (leaf.getMime().startsWith("audio/"))
			{
				_context.recordCache.recordAudioReleased(eltCid, leafCid);
			}
		}
		_context.recordCache.recordMetaDataReleased(eltCid);
		
		_context.recordCache.recordMetaDataPinned(newEltCid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), record.getPublisherKey(), unchangedLeaves.size());
		for (DataElement leaf : unchangedLeaves)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (ElementSpecialType.IMAGE == leaf.getSpecial())
			{
				// This is the thumbnail.
				_context.recordCache.recordThumbnailPinned(newEltCid, leafCid);
			}
			else if (leaf.getMime().startsWith("video/"))
			{
				int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
				_context.recordCache.recordVideoPinned(newEltCid, leafCid, maxEdge);
			}
			else if (leaf.getMime().startsWith("audio/"))
			{
				_context.recordCache.recordAudioPinned(newEltCid, leafCid);
			}
		}
		
		// Now, publish the update.
		_background.requestPublish(result.getIndexToPublish());
	}
}
