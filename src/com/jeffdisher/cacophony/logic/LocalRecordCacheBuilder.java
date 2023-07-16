package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


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
		FutureRead<AbstractIndex> localUserIndex = access.loadCached(lastPublishedIndex, AbstractIndex.DESERIALIZER);
		
		// Load the records and info underneath all of these.
		FutureRead<AbstractRecords> localUserRecords;
		FutureRead<AbstractDescription> localUserDescription;
		try
		{
			localUserRecords = access.loadCached(localUserIndex.get().recordsCid, AbstractRecords.DESERIALIZER);
			localUserDescription = access.loadCached(localUserIndex.get().descriptionCid, AbstractDescription.DESERIALIZER);
		}
		catch (FailedDeserializationException e)
		{
			// We can't see this for data we posted.
			throw Assert.unexpected(e);
		}
		
		// Now that the data is accessible, populate the cache.
		AbstractRecords localStreamRecords;
		AbstractDescription localStreamDescription;
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
		List<FutureKey<AbstractIndex>> followeeIndices = new ArrayList<>();
		for(IpfsKey followee : followees.getAllKnownFollowees())
		{
			followeeIndices.add(new FutureKey<>(followee, access.loadCached(followees.getLastFetchedRootForFollowee(followee), AbstractIndex.DESERIALIZER)));
		}
		
		// Load the records and info underneath all of these.
		List<FutureKey<AbstractRecords>> followeeRecords = new ArrayList<>();
		List<FutureKey<AbstractDescription>> followeeDescriptions = new ArrayList<>();
		for (FutureKey<AbstractIndex> future : followeeIndices)
		{
			AbstractIndex index;
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
				FutureRead<AbstractRecords> records = access.loadCached(index.recordsCid, AbstractRecords.DESERIALIZER);
				followeeRecords.add(new FutureKey<>(future.publicKey, records));
				FutureRead<AbstractDescription> description = access.loadCached(index.descriptionCid, AbstractDescription.DESERIALIZER);
				followeeDescriptions.add(new FutureKey<>(future.publicKey, description));
			}
		}
		
		// Now that the data is accessible, populate the cache.
		for (FutureKey<AbstractRecords> future : followeeRecords)
		{
			Map<IpfsFile, FollowingCacheElement> elementsCachedForUser = followees.snapshotAllElementsForFollowee(future.publicKey);
			AbstractRecords followeeRecordsElt;
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
		for (FutureKey<AbstractDescription> future : followeeDescriptions)
		{
			AbstractDescription description;
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
	 * @param recordCache The cache to modify.
	 * @param cid The CID of the new StreamRecord.
	 * @param record The new record.
	 * @throws IpfsConnectionException
	 */
	public static void updateCacheWithNewUserPost(LocalRecordCache recordCache, IpfsFile cid, AbstractRecord record) throws IpfsConnectionException
	{
		_fetchDataForLocalUserElement(recordCache, cid, record);
	}

	public static void populateUserInfoFromDescription(LocalUserInfoCache cache, IpfsKey key, AbstractDescription description)
	{
		_populateUserInfoFromDescription(cache, key, description);
	}


	private static void _populateElementMapFromLocalUserRoot(IReadingAccess access, LocalRecordCache recordCache, AbstractRecords records) throws IpfsConnectionException
	{
		// Everything is always cached for the local user.
		List<IpfsFile> cids = records.getRecordList();
		List<FutureRead<AbstractRecord>> loads = new ArrayList<>();
		for (IpfsFile cid: cids)
		{
			// Since we posted this, we know it is a valid size.
			Assert.assertTrue(access.getSizeInBytes(cid).get() < AbstractRecord.SIZE_LIMIT_BYTES);
			FutureRead<AbstractRecord> future = access.loadCached(cid, AbstractRecord.DESERIALIZER);
			loads.add(future);
		}
		Iterator<IpfsFile> cidIterator = cids.iterator();
		for (FutureRead<AbstractRecord> future : loads)
		{
			IpfsFile cid = cidIterator.next();
			AbstractRecord record;
			try
			{
				record = future.get();
			}
			catch (FailedDeserializationException e)
			{
				// We can't see this for data we posted.
				throw Assert.unexpected(e);
			}
			_fetchDataForLocalUserElement(recordCache, cid, record);
		}
	}

	private static void _populateElementMapFromFolloweeUserRoot(IReadingAccess access, LocalRecordCache recordCache, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, AbstractRecords records) throws IpfsConnectionException
	{
		// We want to distinguish between records which are cached for this user and which ones aren't.
		// (in theory, multiple users could have an identical element only cached in some of them which could be
		//  displayed for all of them - we will currently ignore that case and only add the last entry).
		List<IpfsFile> cids = records.getRecordList();
		List<FutureRead<AbstractRecord>> loads = new ArrayList<>();
		for (IpfsFile cid : cids)
		{
			// Make sure that this isn't too big (we could adapt the checker for this, since it isn't pinned, but this is more explicit).
			if (access.getSizeInBytes(cid).get() < AbstractRecord.SIZE_LIMIT_BYTES)
			{
				FutureRead<AbstractRecord> future = access.loadCached(cid, AbstractRecord.DESERIALIZER);
				loads.add(future);
			}
		}
		Iterator<IpfsFile> cidIterator = cids.iterator();
		for (FutureRead<AbstractRecord> future : loads)
		{
			IpfsFile cid = cidIterator.next();
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

	private static void _fetchDataForLocalUserElement(LocalRecordCache recordCache, IpfsFile cid, AbstractRecord record) throws IpfsConnectionException
	{
		recordCache.recordMetaDataPinned(cid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussionUrl(), record.getPublisherKey(), record.getExternalElementCount());
		
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

	private static void _fetchDataForFolloweeElement(IReadingAccess access, LocalRecordCache recordCache, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, FutureRead<AbstractRecord> future, IpfsFile cid) throws IpfsConnectionException, FailedDeserializationException
	{
		AbstractRecord record = future.get();
		List<AbstractRecord.Leaf> elements = record.getVideoExtension();
		
		recordCache.recordMetaDataPinned(cid, record.getName(), record.getDescription(), record.getPublishedSecondsUtc(), record.getDiscussionUrl(), record.getPublisherKey(), record.getExternalElementCount());
		
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
					for (AbstractRecord.Leaf leaf : elements)
					{
						if (leafCid.equals(leaf.cid()))
						{
							maxEdge = Math.max(leaf.height(), leaf.width());
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

	private static String _findMimeForLeaf(List<AbstractRecord.Leaf> elements, IpfsFile target)
	{
		String mime = null;
		for (AbstractRecord.Leaf leaf : elements)
		{
			IpfsFile leafCid = leaf.cid();
			if (target.equals(leafCid))
			{
				mime = leaf.mime();
			}
		}
		Assert.assertTrue(null != mime);
		return mime;
	}

	private static void _populateUserInfoFromDescription(LocalUserInfoCache cache, IpfsKey key, AbstractDescription description)
	{
		cache.setUserInfo(key
				, description.getName()
				, description.getDescription()
				, description.getPicCid()
				, description.getEmail()
				, description.getWebsite()
		);
	}


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
