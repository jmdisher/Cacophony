package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.logic.LeafFinder;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * A container of the mutative aspects of the various read-only data projection caches.
 * This class presents a high-level API to manage after-the-fact updates to the states of our various caches.
 * This may eventually transition into a generalized notification system, potentially running in its own thread to
 * shield the caches from racy updates.  For now, at least, that would be over-design so this more specific container is
 * used.
 * NOTE:  This system CANNOT be used for any purpose other than updating these view projections.
 */
public class CacheUpdater
{
	private final LocalRecordCache _recordCache;
	private final LocalUserInfoCache _userInfoCache;
	private final EntryCacheRegistry _entryRegistry;
	private final HomeUserReplyCache _replyCache;

	public CacheUpdater(LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
			, HomeUserReplyCache replyCache
	)
	{
		_recordCache = recordCache;
		_userInfoCache = userInfoCache;
		_entryRegistry = entryRegistry;
		_replyCache = replyCache;
	}

	/**
	 * Called when a new home user has been added.
	 * 
	 * @param publicKey The public key of the user.
	 * @param description The description of the user.
	 */
	public void addedHomeUser(IpfsKey publicKey, AbstractDescription description)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.createHomeUser(publicKey);
		}
		if (null != _userInfoCache)
		{
			LocalRecordCacheBuilder.populateUserInfoFromDescription(_userInfoCache, publicKey, description);
		}
	}

	/**
	 * Called when an existing home user has been removed.
	 * 
	 * @param publicKey The public key of the user.
	 */
	public void removedHomeUser(IpfsKey publicKey)
	{
		if (null != _userInfoCache)
		{
			_userInfoCache.removeUser(publicKey);
		}
		if (null != _entryRegistry)
		{
			_entryRegistry.removeHomeUser(publicKey);
		}
	}

	/**
	 * Called when an existing home user updates their description.
	 * 
	 * @param publicKey The public key of the user.
	 * @param description The new description of the user.
	 */
	public void updatedHomeUserInfo(IpfsKey publicKey, AbstractDescription description)
	{
		if (null != _userInfoCache)
		{
			_userInfoCache.setUserInfo(publicKey, description);
		}
	}

	/**
	 * Called when a new followee has been added.
	 * 
	 * @param publicKey The public key of the followee.
	 * @param description The description of the followee.
	 */
	public void addedFollowee(IpfsKey publicKey, AbstractDescription description)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.createNewFollowee(publicKey);
		}
		if (null != _userInfoCache)
		{
			_userInfoCache.setUserInfo(publicKey, description);
		}
	}

	/**
	 * Called when an existing followee has been removed.
	 * 
	 * @param publicKey The public key of the followee.
	 */
	public void removedFollowee(IpfsKey publicKey)
	{
		if (null != _userInfoCache)
		{
			_userInfoCache.removeUser(publicKey);
		}
		if (null != _entryRegistry)
		{
			_entryRegistry.removeFollowee(publicKey);
		}
	}

	/**
	 * Called when an existing followee updates their description.
	 * 
	 * @param publicKey The public key of the followee.
	 * @param description The new description of the followee.
	 */
	public void updatedFolloweeInfo(IpfsKey publicKey, AbstractDescription description)
	{
		if (null != _userInfoCache)
		{
			_userInfoCache.setUserInfo(publicKey, description);
		}
	}

	/**
	 * Called when a home user adds a new post to their stream.
	 * 
	 * @param publicKey The public key of the user.
	 * @param cid The CID of the post added.
	 * @param record The record which was added at CID.
	 */
	public void addedHomeUserPost(IpfsKey publicKey, IpfsFile cid, AbstractRecord record)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.addLocalElement(publicKey, cid);
		}
		if (null != _recordCache)
		{
			_recordCache.recordMetaDataPinned(cid
					, record.getName()
					, record.getDescription()
					, record.getPublishedSecondsUtc()
					, record.getDiscussionUrl()
					, record.getPublisherKey()
					, record.getReplyTo()
					, record.getExternalElementCount()
			);
			
			LeafFinder leaves = LeafFinder.parseRecord(record);
			if (null != leaves.thumbnail)
			{
				_recordCache.recordThumbnailPinned(cid, leaves.thumbnail);
			}
			if (null != leaves.audio)
			{
				_recordCache.recordAudioPinned(cid, leaves.audio);
			}
			for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
			{
				_recordCache.recordVideoPinned(cid, leaf.cid(), leaf.edgeSize());
			}
		}
		if (null != _replyCache)
		{
			_replyCache.addHomePost(cid);
		}
	}

	/**
	 * Called when a home user removes a post from their stream.
	 * 
	 * @param publicKey The public key of the user.
	 * @param cid The CID of the post removed.
	 * @param record The record at CID which is now removed.
	 */
	public void removedHomeUserPost(IpfsKey publicKey, IpfsFile cid, AbstractRecord record)
	{
		if (null != _replyCache)
		{
			_replyCache.removeHomePost(cid);
		}
		if (null != _recordCache)
		{
			LeafFinder leaves = LeafFinder.parseRecord(record);
			if (null != leaves.thumbnail)
			{
				_recordCache.recordThumbnailReleased(cid, leaves.thumbnail);
			}
			if (null != leaves.audio)
			{
				_recordCache.recordAudioReleased(cid, leaves.audio);
			}
			for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
			{
				_recordCache.recordVideoReleased(cid, leaf.cid(), leaf.edgeSize());
			}
			_recordCache.recordMetaDataReleased(cid);
		}
		if (null != _entryRegistry)
		{
			_entryRegistry.removeLocalElement(publicKey, cid);
		}
	}

	/**
	 * Called when a followee adds a new post to their stream.
	 * 
	 * @param publicKey The public key of the followee.
	 * @param cid The CID of the post added.
	 * @param record The record which was added at CID.
	 */
	public void addedFolloweePost(IpfsKey publicKey
			, IpfsFile cid
			, AbstractRecord record
			, IpfsFile cachedImageOrNull
			, IpfsFile cachedAudioOrNull
			, IpfsFile cachedVideoOrNull
			, int videoEdgeSize
	)
	{
		IpfsFile replyTo = record.getReplyTo();
		if (null != _entryRegistry)
		{
			_entryRegistry.addFolloweeElement(publicKey, cid);
		}
		if (null != _recordCache)
		{
			_recordCache.recordMetaDataPinned(cid
					, record.getName()
					, record.getDescription()
					, record.getPublishedSecondsUtc()
					, record.getDiscussionUrl()
					, record.getPublisherKey()
					, replyTo
					, record.getExternalElementCount()
			);
			if (null != cachedImageOrNull)
			{
				_recordCache.recordThumbnailPinned(cid, cachedImageOrNull);
			}
			if (null != cachedAudioOrNull)
			{
				_recordCache.recordAudioPinned(cid, cachedAudioOrNull);
			}
			if (null != cachedVideoOrNull)
			{
				_recordCache.recordVideoPinned(cid, cachedVideoOrNull, videoEdgeSize);
			}
		}
		if ((null != _replyCache) && (null != replyTo))
		{
			_replyCache.addFolloweePost(cid, replyTo);
		}
	}

	/**
	 * Called when a followee removes a post from their stream.
	 * 
	 * @param publicKey The public key of the followee.
	 * @param cid The CID of the post removed.
	 * @param record The record at CID which is now removed.
	 */
	public void removedFolloweePost(IpfsKey publicKey
			, IpfsFile cid
			, AbstractRecord record
			, IpfsFile cachedImageOrNull
			, IpfsFile cachedAudioOrNull
			, IpfsFile cachedVideoOrNull
			, int videoEdgeSize
	)
	{
		if ((null != _replyCache) && (null != record.getReplyTo()))
		{
			_replyCache.removeFolloweePost(cid);
		}
		if (null != _recordCache)
		{
			if (null != cachedImageOrNull)
			{
				_recordCache.recordThumbnailReleased(cid, cachedImageOrNull);
			}
			if (null != cachedAudioOrNull)
			{
				_recordCache.recordAudioReleased(cid, cachedAudioOrNull);
			}
			if (null != cachedVideoOrNull)
			{
				_recordCache.recordVideoReleased(cid, cachedVideoOrNull, videoEdgeSize);
			}
			_recordCache.recordMetaDataReleased(cid);
		}
		if (null != _entryRegistry)
		{
			_entryRegistry.removeFolloweeElement(publicKey, cid);
		}
	}

	/**
	 * Called when a followee starts or stops being refreshed.
	 * 
	 * @param publicKey The public key of the followee.
	 * @param isRefreshing True if the refresh has started, false if it has finished.
	 */
	public void followeeRefreshInProgress(IpfsKey publicKey, boolean isRefreshing)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.setSpecial(publicKey, isRefreshing ? "Refreshing" : null);
		}
	}
}
