package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Helpers used to build and updated the LocalRecordCache.
 */
public class LocalRecordCacheBuilder
{
	/**
	 * Builds a LocalRecordCache by walking the information on the local node, referenced by the local user's
	 * lastPublishedIndex and the data cached for followees.
	 * 
	 * @param access Read-access to the network and data structures.
	 * @param lastPublishedIndex The local user's last published root index.
	 * @param followees The information cached about the followees.
	 * @return The record cache.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 * @throws FailedDeserializationException Data fetched from the network couldn't be interpreted as meta-data.
	 */
	public static LocalRecordCache buildFolloweeCache(IReadingAccess access, IpfsFile lastPublishedIndex, IFolloweeReading followees) throws IpfsConnectionException, FailedDeserializationException
	{
		IpfsKey ourPublicKey = access.getPublicKey();
		List<FutureKey<StreamIndex>> indices = _loadStreamIndicesNoKey(access, ourPublicKey, lastPublishedIndex, followees);
		List<FutureKey<StreamRecords>> streamRecords = _loadRecords(access, indices);
		
		Map<IpfsFile, LocalRecordCache.Element> dataElements = new HashMap<>();
		for (FutureKey<StreamRecords> elt : streamRecords)
		{
			IpfsKey followeeKey = elt.publicKey();
			Map<IpfsFile, FollowingCacheElement> elementsCachedForUser = followees.snapshotAllElementsForFollowee(followeeKey);
			_populateElementMapFromUserRoot(access, dataElements, elementsCachedForUser, elt.future().get());
		}
		return new LocalRecordCache(dataElements);
	}


	private static void _populateElementMapFromUserRoot(IReadingAccess access, Map<IpfsFile, LocalRecordCache.Element> elementMap, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, StreamRecords records) throws IpfsConnectionException, FailedDeserializationException
	{
		// We want to distinguish between records which are cached for this user and which ones aren't.
		// (in theory, multiple users could have an identical element only cached in some of them which could be
		//  displayed for all of them - we will currently ignore that case and only add the last entry).
		List<String> rawCids = records.getRecord();
		List<FutureRead<StreamRecord>> loads = new ArrayList<>();
		for (String rawCid : rawCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// Make sure that this isn't too big (we could adapt the checker for this, since it isn't pinned, but this is more explicit).
			if (access.getSizeInBytes(cid).get() < SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				FutureRead<StreamRecord> future = access.loadCached(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
				loads.add(future);
			}
		}
		Iterator<String> cidIterator = rawCids.iterator();
		for (FutureRead<StreamRecord> future : loads)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(cidIterator.next());
			LocalRecordCache.Element thisElt = _fetchDataForElement(access, elementsCachedForUser, future, cid);
			elementMap.put(cid, thisElt);
		}
	}

	private static LocalRecordCache.Element _fetchDataForElement(IReadingAccess access, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException, FailedDeserializationException
	{
		StreamRecord record = future.get();
		
		boolean isLocalUser = (null == elementsCachedForUser);
		boolean isCachedFollowee = !isLocalUser && elementsCachedForUser.containsKey(cid);
		// In either of these cases, we will have the data cached.
		boolean isCached = (isLocalUser || isCachedFollowee);
		
		IpfsFile thumbnailCid = null;
		IpfsFile videoCid = null;
		IpfsFile audioCid = null;
		if (isLocalUser)
		{
			// In the local case, we want to look at the rest of the record and figure out what makes most sense since all entries will be pinned.
			List<DataElement> elements = record.getElements().getElement();
			thumbnailCid = _findThumbnail(elements);
			videoCid = _findLargestVideo(elements);
			audioCid = _findAudio(elements);
		}
		else if (isCachedFollowee)
		{
			// If this case, we will use whatever is in the followee cache, since that is what we pinned, but we need to look at the record to see if the leaf element is video or audio.
			FollowingCacheElement cachedElement = elementsCachedForUser.get(cid);
			thumbnailCid = cachedElement.imageHash();
			List<DataElement> elements = record.getElements().getElement();
			IpfsFile leafCid = cachedElement.leafHash();
			if (null != leafCid)
			{
				String leafMime = _findMimeForLeaf(elements, leafCid);
				if (leafMime.startsWith("video/"))
				{
					videoCid = leafCid;
				}
				else if (leafMime.startsWith("audio/"))
				{
					audioCid = leafCid;
				}
				else
				{
					// We don't currently pin anything like this.
					throw Assert.unreachable();
				}
			}
		}
		
		String thumbnailUrl = null;
		String videoUrl = null;
		String audioUrl = null;
		if (isCached)
		{
			// However we found these, they are expected to be in the cache and they should be locally pinned.
			// Note that we can have at most one thumbnail and one video but both are optional and there could be an entry with neither.
			if (null != thumbnailCid)
			{
				thumbnailUrl = access.getCachedUrl(thumbnailCid).toString();
			}
			if (null != videoCid)
			{
				videoUrl = access.getCachedUrl(videoCid).toString();
			}
			if (null != audioCid)
			{
				audioUrl = access.getCachedUrl(audioCid).toString();
			}
		}
		return new LocalRecordCache.Element(record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), isCached, thumbnailUrl, videoUrl, audioUrl);
	}

	private static IpfsFile _findThumbnail(List<DataElement> elements)
	{
		IpfsFile thumbnailCid = null;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (ElementSpecialType.IMAGE == leaf.getSpecial())
			{
				thumbnailCid = leafCid;
				break;
			}
		}
		return thumbnailCid;
	}

	private static IpfsFile _findLargestVideo(List<DataElement> elements)
	{
		IpfsFile videoCid = null;
		int largestEdge = 0;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (leaf.getMime().startsWith("video/"))
			{
				int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
				if (maxEdge > largestEdge)
				{
					videoCid = leafCid;
				}
			}
		}
		return videoCid;
	}

	private static IpfsFile _findAudio(List<DataElement> elements)
	{
		IpfsFile audioCid = null;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (leaf.getMime().startsWith("audio/"))
			{
				audioCid = leafCid;
				break;
			}
		}
		return audioCid;
	}

	private static String _findMimeForLeaf(List<DataElement> elements, IpfsFile target)
	{
		String mime = null;
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (target.equals(leafCid))
			{
				mime = leaf.getMime();
			}
		}
		Assert.assertTrue(null != mime);
		return mime;
	}

	private static List<FutureKey<StreamIndex>> _loadStreamIndicesNoKey(IReadingAccess access, IpfsKey ourPublicKey, IpfsFile lastPublishedIndex, IFolloweeReading followees)
	{
		List<FutureKey<StreamIndex>> indices = new ArrayList<>();
		indices.add(new FutureKey<>(ourPublicKey, access.loadCached(lastPublishedIndex, (byte[] data) -> GlobalData.deserializeIndex(data))));
		for(IpfsKey followee : followees.getAllKnownFollowees())
		{
			indices.add(new FutureKey<>(followee, access.loadCached(followees.getLastFetchedRootForFollowee(followee), (byte[] data) -> GlobalData.deserializeIndex(data))));
		}
		return indices;
	}

	private static List<FutureKey<StreamRecords>> _loadRecords(IReadingAccess access, List<FutureKey<StreamIndex>> list) throws IpfsConnectionException, FailedDeserializationException
	{
		List<FutureKey<StreamRecords>> recordsList = new ArrayList<>();
		for (FutureKey<StreamIndex> future : list)
		{
			StreamIndex index = future.future.get();
			FutureRead<StreamRecords> records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
			recordsList.add(new FutureKey<StreamRecords>(future.publicKey, records));
		}
		return recordsList;
	}


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
