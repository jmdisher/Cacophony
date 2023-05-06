package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LeafFinder;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * This command is really just meant to the be the command-line analogue of GET_PostStruct for testing purposes.
 */
public record ShowPostCommand(IpfsFile _elementCid) implements ICommand<ShowPostCommand.PostDetails>
{
	@Override
	public PostDetails runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		PostDetails post = null;
		// First, check if we have a record cache and if it contains this.
		if (null != context.recordCache)
		{
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
		}
		
		// If we didn't have a cache or didn't find it, try the expensive path to the network (we assume this is NOT cached).
		if (null == post)
		{
			try (IReadingAccess access = StandardAccess.readAccess(context))
			{
				post = _runCore(context.logger, access);
			}
		}
		Assert.assertTrue(null != post);
		return post;
	}


	private PostDetails _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, KeyException, FailedDeserializationException, SizeConstraintException
	{
		// We don't know anything about this so we will just read it as uncached.
		StreamRecord record = access.loadNotCached(_elementCid, "record", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeRecord(data)).get();
		// This fails with exception and never returns null.
		Assert.assertTrue(null != record);
		
		// Format the data for the expected shape (which is, as though we were putting it into the followee cache).
		LeafFinder leaves = LeafFinder.parseRecord(record);
		LeafFinder.VideoLeaf videoLeaf = leaves.largestVideoWithLimit(access.readPrefs().videoEdgePixelMax);
		IpfsFile imageHash = leaves.thumbnail;
		IpfsFile videoHash = (null != videoLeaf)
				? videoLeaf.cid()
				: null
		;
		IpfsFile audioHash = leaves.audio;
		return new PostDetails(_elementCid
				, false
				, record.getName()
				, record.getDescription()
				, record.getPublishedSecondsUtc()
				, record.getDiscussion()
				, record.getPublisherKey()
				, imageHash
				, videoHash
				, audioHash
		);
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
