package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.data.local.v3.Opcode_ExplicitStreamRecordV3;
import com.jeffdisher.cacophony.data.local.v3.Opcode_ExplicitUserInfoV3;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_ExplicitStreamRecord;
import com.jeffdisher.cacophony.data.local.v4.Opcode_ExplicitUserInfo;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The explicit data cache for user info and stream records.  Internally, this manages an LRU and can be told to purge
 * down to a certain size.
 */
public class ExplicitCacheData implements IExplicitCacheReading
{
	// When something is used, it is removed from the list and re-added at the end.
	// This means that element 0 is "least recently used".
	// The same LRU will be used for both IpfsKey and IpfsFile, so a type check is required.
	private final List<Object> _lru;
	private final Lock _lruLock;
	private final Map<IpfsKey, UserInfo> _userInfo;
	private final Map<IpfsFile, CachedRecordInfo> _recordInfo;
	private long _totalCacheInBytes;

	/**
	 * Creates the empty cache.
	 */
	public ExplicitCacheData()
	{
		_lru = new ArrayList<>();
		_lruLock = new ReentrantLock();
		_userInfo = new HashMap<>();
		_recordInfo = new HashMap<>();
	}

	/**
	 * Serializes the contents of the receiver into the given V3 writer.
	 * 
	 * @param writer The writer which will consume the V3 opcodes.
	 * @throws IOException The writer encountered an error.
	 */
	public void serializeToOpcodeWriterV3(OpcodeCodec.Writer writer) throws IOException
	{
		// We walk the LRU in-order from least to most recently used since that is how we re-add them.
		for (Object object : _lru)
		{
			// Check what this is.
			if (object instanceof IpfsKey)
			{
				IpfsKey elt = (IpfsKey) object;
				Assert.assertTrue(_userInfo.containsKey(elt));
				UserInfo info = _userInfo.get(elt);
				writer.writeOpcode(new Opcode_ExplicitUserInfoV3(info.indexCid, info.recommendationsCid, info.descriptionCid, info.userPicCid, info.combinedSizeBytes));
			}
			else
			{
				IpfsFile elt = (IpfsFile) object;
				Assert.assertTrue(_recordInfo.containsKey(elt));
				CachedRecordInfo info = _recordInfo.get(elt);
				writer.writeOpcode(new Opcode_ExplicitStreamRecordV3(info.streamCid(), info.thumbnailCid(), info.videoCid(), info.audioCid(), info.combinedSizeBytes()));
			}
		}
	}

	/**
	 * Serializes the contents of the receiver into the given writer.
	 * 
	 * @param writer The writer which will consume the opcodes.
	 * @throws IOException The writer encountered an error.
	 */
	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		// We walk the LRU in-order from least to most recently used since that is how we re-add them.
		for (Object object : _lru)
		{
			// Check what this is.
			if (object instanceof IpfsKey)
			{
				IpfsKey elt = (IpfsKey) object;
				Assert.assertTrue(_userInfo.containsKey(elt));
				UserInfo info = _userInfo.get(elt);
				Assert.assertTrue(null != info.publicKey);
				Assert.assertTrue(null != info.indexCid);
				Assert.assertTrue(null != info.recommendationsCid);
				Assert.assertTrue(null != info.recordsCid);
				Assert.assertTrue(null != info.descriptionCid);
				writer.writeOpcode(new Opcode_ExplicitUserInfo(info.publicKey
						, info.lastFetchAttemptMillis
						, info.lastFetchSuccessMillis
						, info.indexCid
						, info.recommendationsCid
						, info.recordsCid
						, info.descriptionCid
						, info.userPicCid
						, info.combinedSizeBytes
				));
			}
			else
			{
				IpfsFile elt = (IpfsFile) object;
				Assert.assertTrue(_recordInfo.containsKey(elt));
				CachedRecordInfo info = _recordInfo.get(elt);
				writer.writeOpcode(new Opcode_ExplicitStreamRecord(info.streamCid(), info.hasDataToCache(), info.thumbnailCid(), info.videoCid(), info.audioCid(), info.combinedSizeBytes()));
			}
		}
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
			pin.accept(info.recordsCid);
			pin.accept(info.descriptionCid);
			if (null != info.userPicCid)
			{
				pin.accept(info.userPicCid);
			}
		}
		for(CachedRecordInfo info : _recordInfo.values())
		{
			pin.accept(info.streamCid());
			if (null != info.thumbnailCid())
			{
				pin.accept(info.thumbnailCid());
			}
			if (null != info.videoCid())
			{
				pin.accept(info.videoCid());
			}
			if (null != info.audioCid())
			{
				pin.accept(info.audioCid());
			}
		}
	}

	/**
	 * Adds a new user description to the cache and marks it as most recently used.
	 * NOTE:  A user with the given publicKey cannot already be in the cache.
	 * 
	 * @param publicKey The public key of the user being added.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @param indexCid The CID of the user's StreamIndex root element.
	 * @param recommendationsCid The CID of the user's StreamRecommendations element.
	 * @param recordsCid The CID of the user's StreamRecords element.
	 * @param descriptionCid The CID of the user's StreamDescription element.
	 * @param userPicCid The CID of the user's picture (from StreamDescription).
	 * @param combinedSizeBytes The combined size, in bytes, of all of the above CID elements.
	 * @return The new UserInfo element added.
	 */
	public UserInfo addUserInfo(IpfsKey publicKey, long currentTimeMillis, IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile recordsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes)
	{
		Assert.assertTrue(!_userInfo.containsKey(publicKey));
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != indexCid);
		Assert.assertTrue(null != recommendationsCid);
		Assert.assertTrue(null != recordsCid);
		Assert.assertTrue(null != descriptionCid);
		UserInfo userInfo = new UserInfo(publicKey
				, currentTimeMillis
				, currentTimeMillis
				, indexCid
				, recommendationsCid
				, recordsCid
				, descriptionCid
				, userPicCid
				, combinedSizeBytes
		);
		_userInfo.put(publicKey, userInfo);
		_lru.add(publicKey);
		_totalCacheInBytes += combinedSizeBytes;
		return userInfo;
	}

	/**
	 * Updates an existing user description in the cache after a successful refresh of the data.
	 * 
	 * @param publicKey The public key of the user being added.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @param indexCid The CID of the user's StreamIndex root element.
	 * @param recommendationsCid The CID of the user's StreamRecommendations element.
	 * @param recordsCid The CID of the user's StreamRecords element.
	 * @param descriptionCid The CID of the user's StreamDescription element.
	 * @param userPicCid The CID of the user's picture (from StreamDescription).
	 * @param combinedSizeBytes The combined size, in bytes, of all of the above CID elements.
	 * @return The new UserInfo element added.
	 */
	public UserInfo successRefreshUserInfo(IpfsKey publicKey, long currentTimeMillis, IpfsFile indexCid, IpfsFile recommendationsCid, IpfsFile recordsCid, IpfsFile descriptionCid, IpfsFile userPicCid, long combinedSizeBytes)
	{
		Assert.assertTrue(_userInfo.containsKey(publicKey));
		Assert.assertTrue(null != publicKey);
		Assert.assertTrue(null != indexCid);
		Assert.assertTrue(null != recommendationsCid);
		Assert.assertTrue(null != recordsCid);
		Assert.assertTrue(null != descriptionCid);
		
		UserInfo oldInfo = _userInfo.get(publicKey);
		UserInfo newInfo = new UserInfo(publicKey
				, currentTimeMillis
				, currentTimeMillis
				, indexCid
				, recommendationsCid
				, recordsCid
				, descriptionCid
				, userPicCid
				, combinedSizeBytes
		);
		_totalCacheInBytes -= oldInfo.combinedSizeBytes;
		_totalCacheInBytes += combinedSizeBytes;
		
		_userInfo.put(publicKey, newInfo);
		_updateLru(publicKey);
		return newInfo;
	}

	/**
	 * Updates an existing user description in the cache after a failed refresh of the data.
	 * 
	 * @param publicKey The public key of the user being added.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The new UserInfo element added.
	 */
	public UserInfo failedRefreshUserInfo(IpfsKey publicKey, long currentTimeMillis)
	{
		Assert.assertTrue(_userInfo.containsKey(publicKey));
		Assert.assertTrue(null != publicKey);
		
		UserInfo oldInfo = _userInfo.get(publicKey);
		UserInfo newInfo = new UserInfo(publicKey
				, currentTimeMillis
				, oldInfo.lastFetchSuccessMillis
				, oldInfo.indexCid
				, oldInfo.recommendationsCid
				, oldInfo.recordsCid
				, oldInfo.descriptionCid
				, oldInfo.userPicCid
				, oldInfo.combinedSizeBytes
		);
		
		_userInfo.put(publicKey, newInfo);
		_updateLru(publicKey);
		return newInfo;
	}

	/**
	 * Adds a new StreamRecord to the cache and marks it as most recently used.  Note that thumbnail, video, and audio
	 * are all optional but at most one of video and audio can be present.
	 * NOTE:  A record with the given streamCid cannot already be in the cache.
	 * 
	 * @param streamCid The CID of the StreamRecord.
	 * @param recordInfo The common info of a cached record.
	 */
	public void addStreamRecord(IpfsFile streamCid, CachedRecordInfo recordInfo)
	{
		// NOTE:  There was a bug in the 4.1 release which allowed duplicate stream records to end up in the opcode stream so we just take the last one we see, since it should be the most populated.
		// As of 4.1.1-rc1, this bug shouldn't appear but the work-around is in place, for now.
		if (_recordInfo.containsKey(streamCid))
		{
			// This is the bogus case so remove this from the old info and proceed.
			CachedRecordInfo stale = _recordInfo.get(streamCid);
			_lru.remove(streamCid);
			_totalCacheInBytes -= stale.combinedSizeBytes();
		}
		_recordInfo.put(streamCid, recordInfo);
		_lru.add(streamCid);
		_totalCacheInBytes += recordInfo.combinedSizeBytes();
	}

	/**
	 * Replaces an existing StreamRecord in the cache and marks it as most recently used.  Note that thumbnail, video,
	 * and audio are all optional but at most one of video and audio can be present.
	 * NOTE:  A record with the given streamCid MUST already be in the cache.
	 * 
	 * @param streamCid The CID of the StreamRecord.
	 * @param recordInfo The common info of a cached record.
	 */
	public void replaceStreamRecord(IpfsFile streamCid, CachedRecordInfo recordInfo)
	{
		Assert.assertTrue(_recordInfo.containsKey(streamCid));
		CachedRecordInfo oldInfo = _recordInfo.remove(streamCid);
		_totalCacheInBytes -= oldInfo.combinedSizeBytes();
		_recordInfo.put(streamCid, recordInfo);
		// We want to remove and add this so that is the same as an LRU update - the lock isn't required in this mutable
		// path but should be harmless since this is rare and avoids duplication.
		_updateLru(streamCid);
		_totalCacheInBytes += recordInfo.combinedSizeBytes();
	}

	@Override
	public UserInfo getUserInfo(IpfsKey publicKey)
	{
		UserInfo info = _userInfo.get(publicKey);
		if (null != info)
		{
			// Re-sort this as recently used.
			_updateLru(publicKey);
		}
		return info;
	}

	@Override
	public CachedRecordInfo getRecordInfo(IpfsFile recordCid)
	{
		CachedRecordInfo info = _recordInfo.get(recordCid);
		if (null != info)
		{
			_updateLru(recordCid);
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
			Object object = _lru.remove(0);
			// Check what this is.
			if (object instanceof IpfsKey)
			{
				IpfsKey elt = (IpfsKey) object;
				Assert.assertTrue(_userInfo.containsKey(elt));
				UserInfo info = _userInfo.remove(elt);
				unpin.accept(info.indexCid);
				unpin.accept(info.recommendationsCid);
				unpin.accept(info.descriptionCid);
				if (null != info.userPicCid)
				{
					unpin.accept(info.userPicCid);
				}
				_totalCacheInBytes -= info.combinedSizeBytes;
			}
			else
			{
				IpfsFile elt = (IpfsFile) object;
				Assert.assertTrue(_recordInfo.containsKey(elt));
				CachedRecordInfo info = _recordInfo.remove(elt);
				unpin.accept(info.streamCid());
				if (null != info.thumbnailCid())
				{
					unpin.accept(info.thumbnailCid());
				}
				if (null != info.videoCid())
				{
					unpin.accept(info.videoCid());
				}
				if (null != info.audioCid())
				{
					unpin.accept(info.audioCid());
				}
				_totalCacheInBytes -= info.combinedSizeBytes();
			}
		}
	}

	@Override
	public long getCacheSizeBytes()
	{
		return _totalCacheInBytes;
	};


	private void _updateLru(Object object)
	{
		_lruLock.lock();
		try
		{
			// Re-sort this as recently used.
			boolean didRemove = _lru.remove(object);
			Assert.assertTrue(didRemove);
			_lru.add(object);
		}
		finally
		{
			_lruLock.unlock();
		}
	}


	public static record UserInfo(IpfsKey publicKey
			, long lastFetchAttemptMillis
			, long lastFetchSuccessMillis
			, IpfsFile indexCid
			, IpfsFile recommendationsCid
			, IpfsFile recordsCid
			, IpfsFile descriptionCid
			, IpfsFile userPicCid
			, long combinedSizeBytes
	) {}
}
