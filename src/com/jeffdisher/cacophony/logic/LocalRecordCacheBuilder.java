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
	 * The algorithm attempts to minimize the impact of corrupt data within followees, meaning it will still return
	 * partial data, even if some of that couldn't be interpreted.
	 * 
	 * @param access Read-access to the network and data structures.
	 * @param lastPublishedIndex The local user's last published root index.
	 * @param followees The information cached about the followees.
	 * @return The record cache.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 */
	public static LocalRecordCache buildFolloweeCache(IReadingAccess access, IpfsFile lastPublishedIndex, IFolloweeReading followees) throws IpfsConnectionException
	{
		// First, fetch the local user.
		FutureRead<StreamIndex> localUserIndex = access.loadCached(lastPublishedIndex, (byte[] data) -> GlobalData.deserializeIndex(data));
		
		// Now, fetch the followees.
		List<FutureKey<StreamIndex>> followeeIndices = new ArrayList<>();
		for(IpfsKey followee : followees.getAllKnownFollowees())
		{
			followeeIndices.add(new FutureKey<>(followee, access.loadCached(followees.getLastFetchedRootForFollowee(followee), (byte[] data) -> GlobalData.deserializeIndex(data))));
		}
		
		// Load the records underneath all of these.
		FutureRead<StreamRecords> localUserRecords;
		try
		{
			localUserRecords = access.loadCached(IpfsFile.fromIpfsCid(localUserIndex.get().getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		
		// ...and the followees.
		List<FutureKey<StreamRecords>> followeeRecords = new ArrayList<>();
		for (FutureKey<StreamIndex> future : followeeIndices)
		{
			StreamIndex index;
			try
			{
				index = future.future.get();
			}
			catch (FailedDeserializationException e)
			{
				// In this case, we just want to skip this user.
				index = null;
			}
			if (null != index)
			{
				FutureRead<StreamRecords> records = access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
				followeeRecords.add(new FutureKey<>(future.publicKey, records));
			}
		}
		
		// Now that the data is accessible, create the cache and populate it.
		Map<IpfsFile, LocalRecordCache.Element> dataElements = new HashMap<>();
		StreamRecords localStreamRecords;
		try
		{
			localStreamRecords = localUserRecords.get();
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		_populateElementMapFromLocalUserRoot(access, dataElements, localStreamRecords);
		
		for (FutureKey<StreamRecords> future : followeeRecords)
		{
			Map<IpfsFile, FollowingCacheElement> elementsCachedForUser = followees.snapshotAllElementsForFollowee(future.publicKey);
			StreamRecords followeeRecordsElt;
			try
			{
				followeeRecordsElt = future.future.get();
			}
			catch (FailedDeserializationException e1)
			{
				// We will just skip this user.
				System.err.println("WARNING:  Deserialization error building cache for followee: " + future.publicKey);
				followeeRecordsElt = null;
			}
			if (null != followeeRecordsElt)
			{
				_populateElementMapFromFolloweeUserRoot(access, dataElements, elementsCachedForUser, followeeRecordsElt);
			}
		}
		
		return new LocalRecordCache(dataElements);
	}


	private static void _populateElementMapFromLocalUserRoot(IReadingAccess access, Map<IpfsFile, LocalRecordCache.Element> elementMap, StreamRecords records) throws IpfsConnectionException
	{
		// Everything is always cached for the local user.
		List<String> rawCids = records.getRecord();
		List<FutureRead<StreamRecord>> loads = new ArrayList<>();
		for (String rawCid : rawCids)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// Since we posted this, we know it is a valid size.
			Assert.assertTrue(access.getSizeInBytes(cid).get() < SizeLimits.MAX_RECORD_SIZE_BYTES);
			FutureRead<StreamRecord> future = access.loadCached(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
			loads.add(future);
		}
		Iterator<String> cidIterator = rawCids.iterator();
		for (FutureRead<StreamRecord> future : loads)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(cidIterator.next());
			LocalRecordCache.Element elt = _fetchDataForLocalUserElement(access, future, cid);
			elementMap.put(cid, elt);
		}
	}

	private static void _populateElementMapFromFolloweeUserRoot(IReadingAccess access, Map<IpfsFile, LocalRecordCache.Element> elementMap, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, StreamRecords records) throws IpfsConnectionException
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
			try
			{
				LocalRecordCache.Element elt = _fetchDataForFolloweeElement(access, elementsCachedForUser, future, cid);
				elementMap.put(cid, elt);
			}
			catch (FailedDeserializationException e)
			{
				// We will just skip this element.
				System.err.println("WARNING:  Deserialization error building cache in element: " + cid);
			}
		}
	}

	private static LocalRecordCache.Element _fetchDataForLocalUserElement(IReadingAccess access, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException
	{
		StreamRecord record;
		try
		{
			record = future.get();
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		
		IpfsFile thumbnailCid = null;
		IpfsFile videoCid = null;
		int largestEdge = 0;
		IpfsFile audioCid = null;
		List<DataElement> elements = record.getElements().getElement();
		for (DataElement leaf : elements)
		{
			IpfsFile leafCid = IpfsFile.fromIpfsCid(leaf.getCid());
			if (ElementSpecialType.IMAGE == leaf.getSpecial())
			{
				// This is the thumbnail.
				thumbnailCid = leafCid;
			}
			else if (leaf.getMime().startsWith("video/"))
			{
				int maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
				if (maxEdge > largestEdge)
				{
					// We want to report the largest video
					videoCid = leafCid;
					largestEdge = maxEdge;
				}
			}
			else if (leaf.getMime().startsWith("audio/"))
			{
				audioCid = leafCid;
			}
		}
		
		// Local user elements are always considered cached.
		boolean isCached = true;
		return new LocalRecordCache.Element(isCached, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), thumbnailCid, videoCid, audioCid);
	}

	private static LocalRecordCache.Element _fetchDataForFolloweeElement(IReadingAccess access, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException, FailedDeserializationException
	{
		StreamRecord record = future.get();
		
		IpfsFile thumbnailCid = null;
		IpfsFile videoCid = null;
		IpfsFile audioCid = null;
		List<DataElement> elements = record.getElements().getElement();
		
		// If this is a followee, then check for the appropriate leaves.
		// (note that we want to double-count with local user, if both - since the pin cache will do that).
		FollowingCacheElement cachedElement = elementsCachedForUser.get(cid);
		if (null != cachedElement)
		{
			thumbnailCid = cachedElement.imageHash();
			IpfsFile leafCid = cachedElement.leafHash();
			if (null != leafCid)
			{
				String leafMime = _findMimeForLeaf(elements, leafCid);
				if (leafMime.startsWith("video/"))
				{
					// We want to record the size so go find it.
					int maxEdge = 0;
					for (DataElement leaf : elements)
					{
						if (leafCid.equals(IpfsFile.fromIpfsCid(leaf.getCid())))
						{
							maxEdge = Math.max(leaf.getHeight(), leaf.getWidth());
						}
					}
					Assert.assertTrue(maxEdge > 0);
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
		
		// Followee entries are considered cached if we have a record for it or there are no leaves.
		boolean isCached = ((null != cachedElement) || (0 == elements.size()));
		return new LocalRecordCache.Element(isCached, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), thumbnailCid, videoCid, audioCid);
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


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
