package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * This is temporarily just a container but will later become an actual abstraction.
 * In this first step, it just hides the mutative calls into the caches, since the context only exposes them as
 * read-only.
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

	public void userInfoCache_updateWithNewUserPost(IpfsFile newElement, AbstractRecord newRecord) throws IpfsConnectionException
	{
		if (null != _userInfoCache)
		{
			LocalRecordCacheBuilder.updateCacheWithNewUserPost(_recordCache, _replyCache, newElement, newRecord);
		}
	}

	public void entryRegistry_removeLocalElement(IpfsKey publicKey, IpfsFile recordCid)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.removeLocalElement(publicKey, recordCid);
		}
	}

	public void recordCache_recordMetaDataReleased(IpfsFile recordCid)
	{
		if (null != _recordCache)
		{
			_recordCache.recordMetaDataReleased(recordCid);
		}
	}

	public void recordCache_recordThumbnailReleased(IpfsFile recordCid, IpfsFile thumbnail)
	{
		if (null != _recordCache)
		{
			_recordCache.recordThumbnailReleased(recordCid, thumbnail);
		}
	}

	public void recordCache_recordAudioReleased(IpfsFile recordCid, IpfsFile audio)
	{
		if (null != _recordCache)
		{
			_recordCache.recordAudioReleased(recordCid, audio);
		}
	}

	public void recordCache_recordVideoReleased(IpfsFile recordCid, IpfsFile cid, int edgeSize)
	{
		if (null != _recordCache)
		{
			_recordCache.recordVideoReleased(recordCid, cid, edgeSize);
		}
	}

	public void entryRegistry_addLocalElement(IpfsKey publicKey, IpfsFile newCid)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.addLocalElement(publicKey, newCid);
		}
	}

	public void recordCache_recordMetaDataPinned(IpfsFile newCid, String name, String description, long publishedSecondsUtc, String discussionUrl, IpfsKey publisherKey, IpfsFile replyTo, int externalElementCount)
	{
		if (null != _recordCache)
		{
			_recordCache.recordMetaDataPinned(newCid, name, description, publishedSecondsUtc, discussionUrl, publisherKey, replyTo, externalElementCount);
		}
	}

	public void recordCache_recordThumbnailPinned(IpfsFile newCid, IpfsFile thumbnail)
	{
		if (null != _recordCache)
		{
			_recordCache.recordThumbnailPinned(newCid, thumbnail);
		}
	}

	public void recordCache_recordAudioPinned(IpfsFile newCid, IpfsFile audio)
	{
		if (null != _recordCache)
		{
			_recordCache.recordAudioPinned(newCid, audio);
		}
	}

	public void recordCache_recordVideoPinned(IpfsFile newCid, IpfsFile cid, int edgeSize)
	{
		if (null != _recordCache)
		{
			_recordCache.recordVideoPinned(newCid, cid, edgeSize);
		}
	}

	public void replyCache_removeHomePost(IpfsFile oldCid)
	{
		if (null != _replyCache)
		{
			_replyCache.removeHomePost(oldCid);
		}
	}

	public void replyCache_addHomePost(IpfsFile newCid)
	{
		if (null != _replyCache)
		{
			_replyCache.addHomePost(newCid);
		}
	}

	public void entryRegistry_setSpecial(IpfsKey followeeKey, String special)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.setSpecial(followeeKey, special);
		}
	}

	public void entryRegistry_addFolloweeElement(IpfsKey followeeKey, IpfsFile elementHash)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.addFolloweeElement(followeeKey, elementHash);
		}
	}

	public void replyCache_addFolloweePost(IpfsFile elementHash, IpfsFile replyTo)
	{
		if (null != _replyCache)
		{
			_replyCache.addFolloweePost(elementHash, replyTo);
		}
	}

	public void entryRegistry_removeFolloweeElement(IpfsKey followeeKey, IpfsFile elementHash)
	{
		if (null != _entryRegistry)
		{
			_entryRegistry.removeFolloweeElement(followeeKey, elementHash);
		}
	}

	public void replyCache_removeFolloweePost(IpfsFile elementHash)
	{
		if (null != _replyCache)
		{
			_replyCache.removeFolloweePost(elementHash);
		}
	}
}
