package com.jeffdisher.cacophony.interactive;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.commands.EditPostCommand;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.LeafFinder;
import com.jeffdisher.cacophony.scheduler.CommandRunner;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;

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

	private final CommandRunner _runner;
	private final BackgroundOperations _background;

	public POST_Form_EditPost(CommandRunner runner
			, BackgroundOperations background
	)
	{
		_runner = runner;
		_background = background;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		IpfsKey homePublicKey = IpfsKey.fromPublicKey(pathVariables[0]);
		IpfsFile eltCid = IpfsFile.fromIpfsCid(pathVariables[1]);
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String discussionUrl = formVariables.getIfSingle(VAR_DISCUSSION_URL);
		
		EditPostCommand command = new EditPostCommand(eltCid, name, description, discussionUrl);
		InteractiveHelpers.SuccessfulCommand<OnePost> success = InteractiveHelpers.runCommandAndHandleErrors(response
				, _runner
				, homePublicKey
				, command
		);
		if (null != success)
		{
			OnePost result = success.result();
			Context context = success.context();
			_handleCorrectCase(context, response, eltCid, result);
			
			// Output the new element CID.
			response.setContentType("text/plain");
			response.getWriter().print(result.recordCid.toSafeString());
		}
	}

	private void _handleCorrectCase(Context context, HttpServletResponse response, IpfsFile eltCid, OnePost result)
	{
		// Delete the old entry and add the new one.
		context.entryRegistry.removeLocalElement(context.getSelectedKey(), eltCid);
		IpfsFile newEltCid = result.recordCid;
		context.entryRegistry.addLocalElement(context.getSelectedKey(), newEltCid);
		
		// Account for the change of the CID in the record cache.  Even though we don't change the leaf
		// data, we still need to technically "move" them to the new record CID.
		StreamRecord record = result.streamRecord;
		LeafFinder leaves = LeafFinder.parseRecord(record);
		if (null != leaves.thumbnail)
		{
			context.recordCache.recordThumbnailReleased(eltCid, leaves.thumbnail);
		}
		if (null != leaves.audio)
		{
			context.recordCache.recordAudioReleased(eltCid, leaves.audio);
		}
		for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
		{
			context.recordCache.recordVideoReleased(eltCid, leaf.cid(), leaf.edgeSize());
		}
		context.recordCache.recordMetaDataReleased(eltCid);
		
		context.recordCache.recordMetaDataPinned(newEltCid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), record.getPublisherKey(), record.getElements().getElement().size());
		if (null != leaves.thumbnail)
		{
			context.recordCache.recordThumbnailPinned(newEltCid, leaves.thumbnail);
		}
		if (null != leaves.audio)
		{
			context.recordCache.recordAudioPinned(newEltCid, leaves.audio);
		}
		for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
		{
			context.recordCache.recordVideoPinned(newEltCid, leaf.cid(), leaf.edgeSize());
		}
		
		// Now, publish the update.
		_background.requestPublish(context.keyName, result.getIndexToPublish());
	}
}
