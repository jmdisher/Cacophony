package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
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
	 * Populates the given caches with their initial data, referenced by the local user's lastPublishedIndex.
	 * 
	 * @param access Read-access to the network and data structures.
	 * @param recordCache The record cache to populate with the reachable records.
	 * @param userInfoCache The user info cache to populate from the followees and this user.
	 * @param ourPublicKey The public key of the local user.
	 * @param lastPublishedIndex The local user's last published root index.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 */
	public static void populateInitialCacheForLocalUser(IReadingAccess access
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, IpfsKey ourPublicKey
			, IpfsFile lastPublishedIndex
	) throws IpfsConnectionException
	{
		// First, fetch the local user.
		FutureRead<StreamIndex> localUserIndex = access.loadCached(lastPublishedIndex, (byte[] data) -> GlobalData.deserializeIndex(data));
		
		// Load the records and info underneath all of these.
		FutureRead<StreamRecords> localUserRecords;
		FutureRead<StreamDescription> localUserDescription;
		try
		{
			localUserRecords = access.loadCached(IpfsFile.fromIpfsCid(localUserIndex.get().getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data));
			localUserDescription = access.loadCached(IpfsFile.fromIpfsCid(localUserIndex.get().getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data));
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		
		// Now that the data is accessible, populate the cache.
		StreamRecords localStreamRecords;
		StreamDescription localStreamDescription;
		try
		{
			localStreamRecords = localUserRecords.get();
			localStreamDescription = localUserDescription.get();
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		_populateElementMapFromLocalUserRoot(access, recordCache, localStreamRecords);
		_populateUserInfoFromDescription(userInfoCache, ourPublicKey, localStreamDescription);
	}

	/**
	 * Populates the given caches with their initial data, referenced by the data cached for followees.
	 * The algorithm attempts to minimize the impact of corrupt data within followees, meaning it will still return
	 * partial data, even if some of that couldn't be interpreted.
	 * 
	 * @param access Read-access to the network and data structures.
	 * @param recordCache The record cache to populate with the reachable records.
	 * @param userInfoCache The user info cache to populate from the followees and this user.
	 * @param followees The information cached about the followees.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 */
	public static void populateInitialCacheForFollowees(IReadingAccess access
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, IFolloweeReading followees
	) throws IpfsConnectionException
	{
		// Fetch the followees.
		List<FutureKey<StreamIndex>> followeeIndices = new ArrayList<>();
		for(IpfsKey followee : followees.getAllKnownFollowees())
		{
			followeeIndices.add(new FutureKey<>(followee, access.loadCached(followees.getLastFetchedRootForFollowee(followee), (byte[] data) -> GlobalData.deserializeIndex(data))));
		}
		
		// Load the records and info underneath all of these.
		List<FutureKey<StreamRecords>> followeeRecords = new ArrayList<>();
		List<FutureKey<StreamDescription>> followeeDescriptions = new ArrayList<>();
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
				FutureRead<StreamDescription> description = access.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data));
				followeeDescriptions.add(new FutureKey<>(future.publicKey, description));
			}
		}
		
		// Now that the data is accessible, populate the cache.
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
				_populateElementMapFromFolloweeUserRoot(access, recordCache, elementsCachedForUser, followeeRecordsElt);
			}
		}
		for (FutureKey<StreamDescription> future : followeeDescriptions)
		{
			StreamDescription description;
			try
			{
				description = future.future.get();
			}
			catch (FailedDeserializationException e)
			{
				// We will just skip this user.
				System.err.println("WARNING:  Deserialization error building cache for followee: " + future.publicKey);
				description = null;
			}
			if (null != description)
			{
				_populateUserInfoFromDescription(userInfoCache, future.publicKey, description);
			}
		}
	}

	/**
	 * Updates an existing cache with information related to a new post made by the local user.
	 * 
	 * @param access Network access.
	 * @param recordCache The cache to modify.
	 * @param cid The CID of the new StreamRecord.
	 * @throws IpfsConnectionException
	 */
	public static void updateCacheWithNewUserPost(IReadingAccess access, LocalRecordCache recordCache, IpfsFile cid) throws IpfsConnectionException
	{
		FutureRead<StreamRecord> future = access.loadCached(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
		_fetchDataForLocalUserElement(recordCache, future, cid);
	}


	private static void _populateElementMapFromLocalUserRoot(IReadingAccess access, LocalRecordCache recordCache, StreamRecords records) throws IpfsConnectionException
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
			_fetchDataForLocalUserElement(recordCache, future, cid);
		}
	}

	private static void _populateElementMapFromFolloweeUserRoot(IReadingAccess access, LocalRecordCache recordCache, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, StreamRecords records) throws IpfsConnectionException
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
				_fetchDataForFolloweeElement(access, recordCache, elementsCachedForUser, future, cid);
			}
			catch (FailedDeserializationException e)
			{
				// We will just skip this element.
				System.err.println("WARNING:  Deserialization error building cache in element: " + cid);
			}
		}
	}

	private static void _fetchDataForLocalUserElement(LocalRecordCache recordCache, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException
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
		
		recordCache.recordMetaDataPinned(cid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), record.getPublisherKey(), record.getElements().getElement().size());
		
		// If this is a local user, state that all the files are cached.
		LeafFinder leaves = LeafFinder.parseRecord(record);
		if (null != leaves.thumbnail)
		{
			recordCache.recordThumbnailPinned(cid, leaves.thumbnail);
		}
		if (null != leaves.audio)
		{
			recordCache.recordAudioPinned(cid, leaves.audio);
		}
		for (LeafFinder.VideoLeaf leaf : leaves.sortedVideos)
		{
			recordCache.recordVideoPinned(cid, leaf.cid(), leaf.edgeSize());
		}
	}

	private static void _fetchDataForFolloweeElement(IReadingAccess access, LocalRecordCache recordCache, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, FutureRead<StreamRecord> future, IpfsFile cid) throws IpfsConnectionException, FailedDeserializationException
	{
		StreamRecord record = future.get();
		List<DataElement> elements = record.getElements().getElement();
		
		recordCache.recordMetaDataPinned(cid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussion(), record.getPublisherKey(), elements.size());
		
		// If this is a followee, then check for the appropriate leaves.
		// (note that we want to double-count with local user, if both - since the pin cache will do that).
		FollowingCacheElement cachedElement = elementsCachedForUser.get(cid);
		if (null != cachedElement)
		{
			IpfsFile thumbnailCid = cachedElement.imageHash();
			if (null != thumbnailCid)
			{
				recordCache.recordThumbnailPinned(cid, thumbnailCid);
			}
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
					recordCache.recordVideoPinned(cid, leafCid, maxEdge);
				}
				else if (leafMime.startsWith("audio/"))
				{
					recordCache.recordAudioPinned(cid, leafCid);
				}
				else
				{
					// We don't currently pin anything like this.
					throw Assert.unreachable();
				}
			}
		}
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

	private static void _populateUserInfoFromDescription(LocalUserInfoCache cache, IpfsKey key, StreamDescription description)
	{
		cache.setUserInfo(key
				, description.getName()
				, description.getDescription()
				, IpfsFile.fromIpfsCid(description.getPicture())
				, description.getEmail()
				, description.getWebsite()
		);
	}


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
