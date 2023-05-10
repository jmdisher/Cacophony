package com.jeffdisher.cacophony.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The explicit data cache for user info and stream records.  Internally, this manages an LRU and can be told to purge
 * down to a certain size.
 */
public class ExplicitCacheData
{
	// When something is used, it is removed from the list and re-added at the end.
	// This means that element 0 is "least recently used".
	private final List<IpfsFile> _lru;
	private final Map<IpfsFile, UserInfo> _userInfo;
	private final Map<IpfsFile, RecordInfo> _recordInfo;
	private long _totalCacheInBytes;

	public ExplicitCacheData()
	{
		_lru = new ArrayList<>();
		_userInfo = new HashMap<>();
		_recordInfo = new HashMap<>();
	}

	/**
	 * Calls out to the given pin Consumer for each pinned element from the explicit cache.  Note that this will call
	 * the consumer each time a given element is pinned, even if it is pinned multiple times.
	 * 
	 * @param pin The consumer to be called for each pinned element, each time they are pinned.
	 */
	public void walkAllPins(Consumer<IpfsFile> pin)
	{
		for(UserInfo info : _userInfo.values())
		{
			pin.accept(info.indexCid);
			pin.accept(info.recommendationsCid);
			pin.accept(info.descriptionCid);
			pin.accept(info.userPicCid);
		}
		for(RecordInfo info : _recordInfo.values())
		{
			pin.accept(info.streamCid);
			if (null != info.thumbnailCid)
			{
				pin.accept(info.thumbnailCid);
			}
			if (null != info.videoCid)
			{
				pin.accept(info.videoCid);
			}
			if (null != info.audioCid)
			{
				pin.accept(info.audioCid);
			}
		}
	}

	/**
	 * Adds a new user description to the cache and marks it as most recently used.
	 * NOTE:  A user with the given indexCid cannot already be in the cache.
	 * 
	 * @param indexCid The CID of the user's StreamIndex root element.
	 * @param recommendationsCid The CID of the user's StreamRecommendations element.
	 * @param descriptionCid The CID of the user's StreamDescription element.
	 * @param userPicCid The CID of the user's picture (from StreamDescription).
	 * @param combinedSizeBytes The combined size, in bytes, of all of the above elements.
	 * @return The new UserInfo element added.
	 */
	public UserInfo addUserInfo(IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes)
	{
		Assert.assertTrue(!_userInfo.containsKey(indexCid));
		Assert.assertTrue(!_recordInfo.containsKey(indexCid));
		UserInfo userInfo = new UserInfo(indexCid, recommendationsCid, descriptionCid, userPicCid, combinedSizeBytes);
		_userInfo.put(indexCid, userInfo);
		_lru.add(indexCid);
		_totalCacheInBytes += combinedSizeBytes;
		return userInfo;
	}

	/**
	 * Adds a new StreamRecord to the cache and marks it as most recently used.  Note that thumbnail, video, and audio
	 * are all optional but at most one of video and audio can be present.
	 * NOTE:  A record with the given streamCid cannot already be in the cache.
	 * 
	 * @param streamCid The CID of the StreamRecord.
	 * @param thumbnailCid The CID of the thumbnail selected from this StreamRecord.
	 * @param videoCid The CID of the video element selected from this StreamRecord.
	 * @param audioCid The CID of the audio element selected from this StreamRecord.
	 * @param combinedSizeBytes The combined size, in bytes, of all of the above elements.
	 * @return The new RecordInfo element added.
	 */
	public RecordInfo addStreamRecord(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes)
	{
		Assert.assertTrue(!_userInfo.containsKey(streamCid));
		Assert.assertTrue(!_recordInfo.containsKey(streamCid));
		Assert.assertTrue((null == videoCid) || (null == audioCid));
		RecordInfo recordInfo = new RecordInfo(streamCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
		_recordInfo.put(streamCid, recordInfo);
		_lru.add(streamCid);
		_totalCacheInBytes += combinedSizeBytes;
		return recordInfo;
	}

	/**
	 * Reads the UserInfo of the given user's indexCid.  On success, marks the record as most recently used.
	 * 
	 * @param indexCid The CID of the user's StreamIndex.
	 * @return The UserInfo for the user (null if not found).
	 */
	public UserInfo getUserInfo(IpfsFile indexCid)
	{
		UserInfo info = _userInfo.get(indexCid);
		if (null != info)
		{
			// Re-sort this as recently used.
			_lru.remove(indexCid);
			_lru.add(indexCid);
		}
		return info;
	}

	/**
	 * Reads the RecordInfo of the given StreamRecord's recordCid.  On success, marks the record as most recently used.
	 * 
	 * @param recordCid The CID of the StreamRecord.
	 * @return The RecordInfo for the record (null if not found).
	 */
	public RecordInfo getRecordInfo(IpfsFile recordCid)
	{
		RecordInfo info = _recordInfo.get(recordCid);
		if (null != info)
		{
			// Re-sort this as recently used.
			_lru.remove(recordCid);
			_lru.add(recordCid);
		}
		return info;
	}

	/**
	 * Removes least recently used elements from the cache until its total size is below the given cacheLimitInBytes.
	 * 
	 * @param unpin The consumer which will unpin the given CIDs it is given.
	 * @param cacheLimitInBytes The maximum size of the cache, after this method returns.
	 */
	public void purgeCacheToSize(Consumer<IpfsFile> unpin, long cacheLimitInBytes)
	{
		while (_totalCacheInBytes > cacheLimitInBytes)
		{
			IpfsFile elt = _lru.remove(0);
			// Check what this is.
			if (_userInfo.containsKey(elt))
			{
				UserInfo info = _userInfo.remove(elt);
				unpin.accept(info.indexCid);
				unpin.accept(info.recommendationsCid);
				unpin.accept(info.descriptionCid);
				unpin.accept(info.userPicCid);
				_totalCacheInBytes -= info.combinedSizeBytes;
			}
			else
			{
				Assert.assertTrue(_recordInfo.containsKey(elt));
				RecordInfo info = _recordInfo.remove(elt);
				unpin.accept(info.streamCid);
				if (null != info.thumbnailCid)
				{
					unpin.accept(info.thumbnailCid);
				}
				if (null != info.videoCid)
				{
					unpin.accept(info.videoCid);
				}
				if (null != info.audioCid)
				{
					unpin.accept(info.audioCid);
				}
				_totalCacheInBytes -= info.combinedSizeBytes;
			}
		}
	}


	public static record UserInfo(IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes) {};
	public static record RecordInfo(IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes) {}
}
