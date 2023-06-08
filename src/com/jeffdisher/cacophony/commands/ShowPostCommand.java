package com.jeffdisher.cacophony.commands;

import java.io.PrintStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.logic.ExplicitCacheLogic;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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
	public PostDetails runInContext(Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
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
			post = _checkHeavyCaches(context);
		}
		else if (!post.isKnownToBeCached)
		{
			// See if we can override this with a more concretely cached element from the explicit cache.
			// TODO:  Remove this access call when we optimize explicit cache access.
			try (IWritingAccess access = StandardAccess.writeAccess(context))
			{
				CachedRecordInfo existingInfo = ExplicitCacheLogic.getExistingRecordInfo(access, _elementCid);
				// This can be null since we are just checking if it already has the info.
				if (null != existingInfo)
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

	private PostDetails _checkHeavyCaches(Context context) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		PostDetails post;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			// We will check the favourites cache here, too, as the explicit cache MUST be last.
			// (we could use a read-only pass to check the favourites cache but no point in reading twice).
			post = _checkFavouritesCache(access);
			
			if (null == post)
			{
				// Consult the explicit cache - this never returns null but will throw on error.
				post = _checkExplicitCache(access);
			}
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

	private PostDetails _checkExplicitCache(IWritingAccess access) throws ProtocolDataException, IpfsConnectionException, FailedDeserializationException
	{
		CachedRecordInfo info = ExplicitCacheLogic.loadRecordInfo(access, _elementCid);
		// Everything in the explicit cache is cached.
		return _buildDetailsWithCachedInfo(access, info);
	}

	private PostDetails _buildDetailsWithCachedInfo(IReadingAccess access, CachedRecordInfo info) throws IpfsConnectionException, FailedDeserializationException
	{
		StreamRecord record = access.loadCached(info.streamCid(), (byte[] data) -> GlobalData.deserializeRecord(data)).get();
		return new PostDetails(_elementCid
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
