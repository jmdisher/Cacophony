package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.ExplicitCacheLogic;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This command is really just meant to the be the command-line analogue of GET_PostStruct for testing purposes.
 */
public record ShowPostCommand(IpfsFile _elementCid) implements ICommand<ShowPostCommand.PostDetails>
{
	@Override
	public PostDetails runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		PostDetails post = null;
		// First, check if we have a record cache and if it contains this.
		if (null != context.recordCache)
		{
			post = _checkKnownCache(context);
		}
		
		// If we didn't have a cache or didn't find it, try the expensive path through the explicit cache.
		if (null == post)
		{
			post = _checkExplicitCache(context);
		}
		Assert.assertTrue(null != post);
		return post;
	}


	private PostDetails _checkKnownCache(ICommand.Context context)
	{
		PostDetails post = null;
		LocalRecordCache.Element element = context.recordCache.get(_elementCid);
		if (null != element)
		{
			post = new PostDetails(_elementCid
					, element.isCached()
					, element.name()
					, element.description()
					, element.publishedSecondsUtc()
					, element.discussionUrl()
					, element.publisherKey()
					, element.thumbnailCid()
					, element.videoCid()
					, element.audioCid()
			);
		}
		return post;
	}

	private PostDetails _checkExplicitCache(ICommand.Context context) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		PostDetails post;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// Consult the cache - this never returns null but will throw on error.
			ExplicitCacheData.RecordInfo info = ExplicitCacheLogic.loadRecordInfo(access, _elementCid);
			// Everything in the explicit cache is cached.
			StreamRecord record = access.loadCached(info.streamCid(), (byte[] data) -> GlobalData.deserializeRecord(data)).get();
			post = new PostDetails(_elementCid
					, true
					, record.getName()
					, record.getDescription()
					, record.getPublishedSecondsUtc()
					, record.getDiscussion()
					, record.getPublisherKey()
					, info.thumbnailCid()
					, info.videoCid()
					, info.audioCid()
			);
		}
		return post;
	}


	public static record PostDetails(IpfsFile elementCid
			, boolean isKnownToBeCached
			, String name
			, String description
			, long publishedSecondsUtc
			, String discussionUrl
			, String publisherKey
			, IpfsFile thumbnailCid
			, IpfsFile videoCid
			, IpfsFile audioCid
	) implements ICommand.Result
	{
		@Override
		public IpfsFile getIndexToPublish()
		{
			// This command is read-only.
			return null;
		}
		@Override
		public void writeHumanReadable(PrintStream output)
		{
			output.println("Post details:");
			output.println("\tCached state: " + (this.isKnownToBeCached ? "CACHED" : "UNKNOWN"));
			output.println("\tName: " + this.name);
			output.println("\tDescription: " + this.description);
			output.println("\tPublished time (seconds since UTC): " + this.publishedSecondsUtc);
			if (null != this.discussionUrl)
			{
				output.println("\tDiscussion URL: " + this.discussionUrl);
			}
			output.println("\tPublisher: " + this.publisherKey);
			if (null != this.thumbnailCid)
			{
				output.println("\tThumbnail: " + this.thumbnailCid);
			}
			if (null != this.videoCid)
			{
				output.println("\tVideo: " + this.videoCid);
			}
			if (null != this.audioCid)
			{
				output.println("\tAudio: " + this.audioCid);
			}
		}
	}
}
