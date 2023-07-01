package com.jeffdisher.cacophony.logic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This is used as a container of StreamRecord instances which we know something about locally.  This includes the
 * records authored by the channel owner as well as the records authored by users they are following.
 * The cache is incrementally updated as the system runs and all access is synchronized, since reads and writes can come
 * from different threads in the system, at any time.
 * Note that, since it is possible to pin the same element through multiple paths, a list of possible video CIDs is
 * stored for each pinned copy, along with its edge size.  When an element is requested, it is returned with the largest
 * of these representations.
 */
public class LocalRecordCache
{
	private final Map<IpfsFile, InternalElement> _cache;

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

	/**
	 * @param cid The CID of the StreamRecord to look up.
	 * @return The cached data for this StreamRecord or null if it isn't in the cache.
	 */
	public synchronized Element get(IpfsFile cid)
	{
		InternalElement internal = _cache.get(cid);
		Element elt = null;
		if (null != internal)
		{
			elt = new Element(internal.isCached()
					, internal.name
					, internal.description
					, internal.publishedSecondsUtc
					, internal.discussionUrl
					, internal.publisherKey
					, internal.thumbnail
					, internal.largestVideo()
					, internal.audio
			);
		}
		return elt;
	}

	/**
	 * Records that the meta-data element for this StreamRecord has been pinned locally.
	 * Note that this expects to be called multiple times when referenced multiple times.
	 * 
	 * @param cid The CID of the meta-data.
	 * @param name The name.
	 * @param description The description.
	 * @param publishedSecondsUtc The publish time.
	 * @param discussionUrl The discussion URL (can be null).
	 * @param publisherKey The key of the element publisher.
	 * @param leafElementCount The number of leaf elements referenced.
	 */
	public synchronized void recordMetaDataPinned(IpfsFile cid, String name, String description, long publishedSecondsUtc, String discussionUrl, IpfsKey publisherKey, int leafElementCount)
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
				, name
				, description
				, publishedSecondsUtc
				, discussionUrl
				, publisherKey
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
					, previous.name
					, previous.description
					, previous.publishedSecondsUtc
					, previous.discussionUrl
					, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
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
				, previous.name
				, previous.description
				, previous.publishedSecondsUtc
				, previous.discussionUrl
				, previous.publisherKey
				, previous.leafElementCount
				, previous.thumbnail
				, previous.thumbnailRef
				, previous.audio
				, previous.audioRef
				, videos
		));
	}


	/**
	 * A description of the data we have cached for a given StreamRecord.
	 * This is the version of the data which is considered valid for external consumption as it avoids internal details.
	 */
	public static record Element(boolean isCached
			, String name
			, String description
			, long publishedSecondsUtc
			, String discussionUrl
			, IpfsKey publisherKey
			, IpfsFile thumbnailCid
			, IpfsFile videoCid
			, IpfsFile audioCid
	)
	{
	}

	/**
	 * A description of the data in the internal cache for a given StreamRecord CID.  This includes things like
	 * reference counts and potentially different video CIDs so that the cache can be maintained precisely.
	 */
	private static record InternalElement(int refCount
			, String name
			, String description
			, long publishedSecondsUtc
			, String discussionUrl
			, IpfsKey publisherKey
			, int leafElementCount
			, IpfsFile thumbnail
			, int thumbnailRef
			, IpfsFile audio
			, int audioRef
			, VideoReference[] videos
	)
	{
		public boolean isCached()
		{
			// This is cached if we have any leaf referenced cached or if there are no leaf references.
			return (0 == leafElementCount)
					|| (null != thumbnail)
					|| (null != audio)
					|| (videos.length > 0);
		}
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
