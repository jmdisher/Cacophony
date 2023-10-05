package com.jeffdisher.cacophony.caches;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is used as a container of StreamRecord instances which we know are reachable, and at least partially cached,
 * based on whole channels we cache locally.  Those are the home channels (since they live here) and the channels which
 * we are actively following.  The reason for this cache is to avoid needing to search through the record lists for all
 * of these channels in order to find out if a given record is one we know about.
 * The cache is incrementally updated as the system runs and all access is synchronized, since reads and writes can come
 * from different threads in the system, at any time.
 * Note that, since it is possible to pin the same element through multiple paths, a list of possible video CIDs is
 * stored for each pinned copy, along with its edge size.  When an element is requested, it is returned with the largest
 * of these representations.
 * NOTE:  This cache does NOT contain any information about the records cached as favourites or via the explicit cache
 * (although the explicit cache may overlap with this cache if we have explicitly cached leaf elements associated with a
 * followee's post which only has meta-data in the followee cache.
 */
public class LocalRecordCache implements ILocalRecordCache
{
	private final Map<IpfsFile, InternalElement> _cache;

	/**
	 * Creates an empty record cache.
	 */
	public LocalRecordCache()
	{
		_cache = new HashMap<>();
	}

	/**
	 * @return The set of all StreamRecord CIDs known to the cache.
	 */
	public synchronized Set<IpfsFile> getKeys()
	{
		return Collections.unmodifiableSet(_cache.keySet());
	}

	@Override
	public synchronized CachedRecordInfo get(IpfsFile cid)
	{
		InternalElement internal = _cache.get(cid);
		CachedRecordInfo elt = null;
		if (null != internal)
		{
			// We determine if there is other data we could cache by looking at the total number of external leaves if we haven't cached anything.
			// Even though we might NOT CHOOSE to cache these leaves (they may just be large videos, etc), that would be dynamically decided by prefs.
			boolean hasDataToCache = (internal.leafElementCount > 0)
					&& (null == internal.thumbnail)
					&& (0 == internal.videos.length)
					&& (null == internal.audio)
			;
			
			// This is just a live cache so we don't care about the size.
			long combinedSizeBytes = 0L;
			elt = new CachedRecordInfo(cid, hasDataToCache, internal.thumbnail, internal.largestVideo(), internal.audio, combinedSizeBytes);
		}
		return elt;
	}

	/**
	 * Records that the meta-data element for this StreamRecord has been pinned locally.  This is called for both
	 * followee records and home channel records.
	 * Note that this expects to be called multiple times when referenced multiple times (since the same post can appear
	 * in the post list for multiple users).
	 * 
	 * @param cid The CID of the meta-data.
	 * @param leafElementCount The number of leaf elements referenced by this meta-data (whether or not they are pinned).
	 */
	public synchronized void recordMetaDataPinned(IpfsFile cid, int leafElementCount)
	{
		int refCount = 0;
		IpfsFile thumbnail = null;
		int thumbnailRef = 0;
		IpfsFile audio = null;
		int audioRef = 0;
		VideoReference[] videos = null;
		InternalElement previous = _cache.remove(cid);
		if (null != previous)
		{
			refCount = previous.refCount + 1;
			thumbnail = previous.thumbnail;
			thumbnailRef = previous.thumbnailRef;
			audio = previous.audio;
			audioRef = previous.audioRef;
			videos = previous.videos;
		}
		else
		{
			refCount = 1;
			videos = new VideoReference[0];
		}
		Assert.assertTrue(!_cache.containsKey(cid));
		_cache.put(cid, new InternalElement(refCount
				, leafElementCount
				, thumbnail
				, thumbnailRef
				, audio
				, audioRef
				, videos
		));
	}

	/**
	 * Records that the meta-data element for this StreamRecord has been released locally.
	 * Note that this expects to be called multiple times when released multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 */
	public synchronized void recordMetaDataReleased(IpfsFile cid)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are removing it.
		Assert.assertTrue(null != previous);
		
		int newRef = previous.refCount - 1;
		if (newRef > 0)
		{
			_cache.put(cid, new InternalElement(newRef
					, previous.leafElementCount
					, previous.thumbnail
					, previous.thumbnailRef
					, previous.audio
					, previous.audioRef
					, previous.videos
			));
		}
		else
		{
			// We are dropping this so make sure that all the previous leaves have been released.
			Assert.assertTrue(0 == previous.thumbnailRef);
			Assert.assertTrue(0 == previous.audioRef);
			Assert.assertTrue(0 == previous.videos.length);
		}
	}

	/**
	 * Records that the thumbnail associated with this record has been pinned.
	 * Note that this expects to be called multiple times when referenced multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param thumbnail The CID of the thumbnail.
	 */
	public synchronized void recordThumbnailPinned(IpfsFile cid, IpfsFile thumbnail)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		int thumbnailRef = previous.thumbnailRef;
		if (thumbnailRef > 0)
		{
			Assert.assertTrue(previous.thumbnail.equals(thumbnail));
		}
		if (null != thumbnail)
		{
			thumbnailRef += 1;
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, thumbnail
				, thumbnailRef
				, previous.audio
				, previous.audioRef
				, previous.videos
		));
	}

	/**
	 * Records that audio associated with this record has been pinned.
	 * Note that this expects to be called multiple times when referenced multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param audio The CID of the audio.
	 */
	public synchronized void recordAudioPinned(IpfsFile cid, IpfsFile audio)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		int audioRef = previous.audioRef;
		if (audioRef > 0)
		{
			Assert.assertTrue(previous.audio.equals(audio));
		}
		if (null != audio)
		{
			audioRef += 1;
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, previous.thumbnail
				, previous.thumbnailRef
				, audio
				, audioRef
				, previous.videos
		));
	}

	/**
	 * Records that video associated with this record has been pinned.
	 * Note that this expects to be called multiple times when referenced multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param video The CID of the video.
	 * @param videoEdge The longest edge of the video.
	 */
	public synchronized void recordVideoPinned(IpfsFile cid, IpfsFile video, int videoEdge)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		VideoReference[] videos = previous.videos;
		if (null != video)
		{
			VideoReference[] oldVideos = videos;
			videos = new VideoReference[oldVideos.length + 1];
			System.arraycopy(oldVideos, 0, videos, 0, oldVideos.length);
			videos[oldVideos.length] = new VideoReference(video, videoEdge);
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, previous.thumbnail
				, previous.thumbnailRef
				, previous.audio
				, previous.audioRef
				, videos
		));
	}

	/**
	 * Records that the thumbnail for this StreamRecord has been released locally.
	 * Note that this expects to be called multiple times when released multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param thumbnail The CID of the thumbnail.
	 */
	public synchronized void recordThumbnailReleased(IpfsFile cid, IpfsFile thumbnail)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		int thumbnailRef = previous.thumbnailRef;
		if (thumbnailRef > 0)
		{
			Assert.assertTrue(previous.thumbnail.equals(thumbnail));
			thumbnailRef -= 1;
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, (thumbnailRef > 0) ? previous.thumbnail : null
				, thumbnailRef
				, previous.audio
				, previous.audioRef
				, previous.videos
		));
	}

	/**
	 * Records that the audio for this StreamRecord has been released locally.
	 * Note that this expects to be called multiple times when released multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param audio The CID of the audio.
	 */
	public synchronized void recordAudioReleased(IpfsFile cid, IpfsFile audio)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		int audioRef = previous.audioRef;
		if (audioRef > 0)
		{
			Assert.assertTrue(previous.audio.equals(audio));
			audioRef -= 1;
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, previous.thumbnail
				, previous.thumbnailRef
				, (audioRef > 0) ? previous.audio : null
				, audioRef
				, previous.videos
		));
	}

	/**
	 * Records that the video for this StreamRecord has been released locally.
	 * Note that this expects to be called multiple times when released multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param video The CID of the video.
	 * @param videoEdge The longest edge of the video.
	 */
	public synchronized void recordVideoReleased(IpfsFile cid, IpfsFile video, int videoEdge)
	{
		InternalElement previous = _cache.remove(cid);
		// This must be here if we are changing files in it.
		Assert.assertTrue(null != previous);
		
		VideoReference[] videos = previous.videos;
		if (null != video)
		{
			Assert.assertTrue(videos.length > 0);
			VideoReference[] oldVideos = videos;
			videos = new VideoReference[oldVideos.length - 1];
			int index = 0;
			for (VideoReference ref : oldVideos)
			{
				if ((ref.edgeSize == videoEdge) && (ref.file.equals(video)))
				{
					// We skip this one.
				}
				else
				{
					videos[index] = ref;
					index += 1;
				}
			}
			// Make sure that we walked everything and only skipped a single entry.
			Assert.assertTrue(videos.length == index);
		}
		_cache.put(cid, new InternalElement(previous.refCount
				, previous.leafElementCount
				, previous.thumbnail
				, previous.thumbnailRef
				, previous.audio
				, previous.audioRef
				, videos
		));
	}

	/**
	 * A description of the data in the internal cache for a given StreamRecord CID.  This includes things like
	 * reference counts and potentially different video CIDs so that the cache can be maintained precisely.
	 */
	private static record InternalElement(int refCount
			, int leafElementCount
			, IpfsFile thumbnail
			, int thumbnailRef
			, IpfsFile audio
			, int audioRef
			, VideoReference[] videos
	)
	{
		public IpfsFile largestVideo()
		{
			IpfsFile video = null;
			int size = 0;
			for (VideoReference ref : videos)
			{
				if (ref.edgeSize > size)
				{
					video = ref.file;
					size = ref.edgeSize;
				}
			}
			return video;
		}
	}

	private static record VideoReference(IpfsFile file, int edgeSize)
	{
	}
}
