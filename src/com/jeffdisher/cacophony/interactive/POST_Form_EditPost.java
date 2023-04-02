package com.jeffdisher.cacophony.interactive;

import java.util.List;

import com.jeffdisher.breakwater.StringMultiMap;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.EditEntry;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
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

	private final IEnvironment _environment;
	private final ILogger _logger;
	private final BackgroundOperations _background;
	private final LocalRecordCache _recordCache;
	private final EntryCacheRegistry _entryRegistry;

	public POST_Form_EditPost(IEnvironment environment
			, ILogger logger
			, BackgroundOperations background
			, LocalRecordCache recordCache
			, EntryCacheRegistry entryRegistry
	)
	{
		_environment = environment;
		_logger = logger;
		_background = background;
		_recordCache = recordCache;
		_entryRegistry = entryRegistry;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, String[] pathVariables, StringMultiMap<String> formVariables) throws Throwable
	{
		// Make sure that we have all the fields we want - we will assume that we need all the fields, just to keep things simple.
		IpfsFile eltCid = IpfsFile.fromIpfsCid(pathVariables[0]);
		String name = formVariables.getIfSingle(VAR_NAME);
		String description = formVariables.getIfSingle(VAR_DESCRIPTION);
		String discussionUrl = formVariables.getIfSingle(VAR_DISCUSSION_URL);
		// At least one of these must be non-null:  null means "not changing".
		if ((null != name)
				|| (null != description)
				|| (null != discussionUrl)
		)
		{
			try (IWritingAccess access = StandardAccess.writeAccess(_environment, _logger))
			{
				EditEntry.Result result = EditEntry.run(access, eltCid, name, description, discussionUrl);
				if (null != result)
				{
					// Delete the old entry and add the new one.
					_entryRegistry.removeLocalElement(eltCid);
					IpfsFile newEltCid = result.newRecordCid();
					_entryRegistry.addLocalElement(newEltCid);
					
					// Account for the change of the CID in the record cache.  Even though we don't change the leaf
					// data, we still need to technically "move" them to the new record CID.
					StreamRecord record = result.newRecord();
					List<DataElement> unchangedLeaves = record.getElements().getElement();
					for (DataElement leaf : unchangedLeaves)
					{
						IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
						if (ElementSpecialType.IMAGE == leaf.getSpecial())
						{
							// This is the thumbnail.
							_recordCache.recordThumbnailReleased(eltCid, leafCid);
						}
						else if (leaf.getMime().startsWith("video/"))
						{
							int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
							_recordCache.recordVideoReleased(eltCid, leafCid, maxEdge);
						}
						else if (leaf.getMime().startsWith("audio/"))
						{
							_recordCache.recordAudioReleased(eltCid, leafCid);
						}
					}
					_recordCache.recordMetaDataReleased(eltCid);
					
					_recordCache.recordMetaDataPinned(newEltCid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), record.getPublisherKey(), unchangedLeaves.size());
					for (DataElement leaf : unchangedLeaves)
					{
						IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
						if (ElementSpecialType.IMAGE == leaf.getSpecial())
						{
							// This is the thumbnail.
							_recordCache.recordThumbnailPinned(newEltCid, leafCid);
						}
						else if (leaf.getMime().startsWith("video/"))
						{
							int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
							_recordCache.recordVideoPinned(newEltCid, leafCid, maxEdge);
						}
						else if (leaf.getMime().startsWith("audio/"))
						{
							_recordCache.recordAudioPinned(newEltCid, leafCid);
						}
					}
					
					// Now, publish the update.
					_background.requestPublish(result.newRoot());
					
					// Output the new element CID.
					response.setContentType("text/plain");
					response.getWriter().print(newEltCid.toSafeString());
					response.setStatus(HttpServletResponse.SC_OK);
				}
				else
				{
					// Not found.
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		}
		else
		{
			// Missing variables.
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
