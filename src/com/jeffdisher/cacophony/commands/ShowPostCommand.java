package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.caches.ILocalRecordCache;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This command is really just meant to the be the command-line analogue of GET_PostStruct for testing purposes.
 * The _forceCache argument will cause the call to explicitly cache any leaves which aren't already cached.
 */
public record ShowPostCommand(IpfsFile _elementCid, boolean _forceCache) implements ICommand<ShowPostCommand.PostDetails>
{
	@Override
	public PostDetails runInContext(Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		PostDetails post = null;
		// First, check if we have a record cache and if it contains this.
		if (null != context.recordCache)
		{
			post = _checkKnownCache(context);
			// If we found the post, we want to force cached data, and we know that this post isn't cached, ignore the non-cached reference.
			if ((null != post) && _forceCache && !post.isKnownToBeCached)
			{
				post = null;
			}
		}
		
		// If we didn't have a cache or didn't find it, try the expensive path through the explicit cache.
		if (null == post)
		{
			post = _checkHeavyCaches(context);
		}
		else if (!post.isKnownToBeCached)
		{
			// See if we can override this with a more concretely cached element from the explicit cache.
			CachedRecordInfo existingInfo = context.getExplicitCache().getExistingRecord(_elementCid);
			// This can be null since we are just checking if it already has the info.
			if (null != existingInfo)
			{
				try (IReadingAccess access = StandardAccess.readAccess(context))
				{
					post = _buildDetailsWithCachedInfo(access, existingInfo);
				}
				// (if not, we will just default to the non-cached version).
			}
		}
		Assert.assertTrue(null != post);
		return post;
	}


	private PostDetails _checkKnownCache(Context context)
	{
		PostDetails post = null;
		ILocalRecordCache.Element element = context.recordCache.get(_elementCid);
		if (null != element)
		{
			post = new PostDetails(_elementCid
					, element.isCached()
					, element.name()
					, element.description()
					, element.publishedSecondsUtc()
					, element.discussionUrl()
					, element.publisherKey()
					, element.replyToCid()
					, element.thumbnailCid()
					, element.videoCid()
					, element.audioCid()
			);
		}
		return post;
	}

	private PostDetails _checkHeavyCaches(Context context) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		PostDetails post;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// We will check the favourites cache here, too, as the explicit cache MUST be last.
			// (we could use a read-only pass to check the favourites cache but no point in reading twice).
			post = _checkFavouritesCache(access);
		}
		
		if (null == post)
		{
			// Consult the explicit cache - this never returns null but will throw on error.
			post = _checkExplicitCache(context);
		}
		return post;
	}

	private PostDetails _checkFavouritesCache(IReadingAccess access) throws ProtocolDataException, IpfsConnectionException, FailedDeserializationException
	{
		IFavouritesReading favourites = access.readableFavouritesCache();
		CachedRecordInfo info = favourites.getRecordInfo(_elementCid);
		PostDetails post = null;
		if (null != info)
		{
			// Everything in the favourites cache is cached.
			post = _buildDetailsWithCachedInfo(access, info);
		}
		return post;
	}

	private PostDetails _checkExplicitCache(Context context) throws ProtocolDataException, IpfsConnectionException, FailedDeserializationException
	{
		CachedRecordInfo info = context.getExplicitCache().loadRecord(_elementCid).get();
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			// Everything in the explicit cache is cached.
			return _buildDetailsWithCachedInfo(access, info);
		}
	}

	private PostDetails _buildDetailsWithCachedInfo(IReadingAccess access, CachedRecordInfo info) throws IpfsConnectionException, FailedDeserializationException
	{
		AbstractRecord record = access.loadCached(info.streamCid(), AbstractRecord.DESERIALIZER).get();
		return new PostDetails(_elementCid
				, true
				, record.getName()
				, record.getDescription()
				, record.getPublishedSecondsUtc()
				, record.getDiscussionUrl()
				, record.getPublisherKey()
				, record.getReplyTo()
				, info.thumbnailCid()
				, info.videoCid()
				, info.audioCid()
		);
	}


	public static record PostDetails(IpfsFile elementCid
			, boolean isKnownToBeCached
			, String name
			, String description
			, long publishedSecondsUtc
			, String discussionUrl
			, IpfsKey publisherKey
			, IpfsFile replyToCid
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
			output.println("\tPublisher: " + this.publisherKey.toPublicKey());
			if (null != this.replyToCid)
			{
				output.println("\tReply to: " + this.replyToCid);
			}
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
