package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
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
	 * @param replyCache The replyTo cache which must be populated with the home users' post CIDs.
	 * @param ourPublicKey The public key of the local user.
	 * @param lastPublishedIndex The local user's last published root index.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 */
	public static void populateInitialCacheForLocalUser(IReadingAccess access
			, CacheUpdater cacheUpdater
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
		cacheUpdater.addedHomeUser(ourPublicKey, localStreamDescription);
		_populateElementMapFromLocalUserRoot(access, cacheUpdater, ourPublicKey, localStreamRecords);
	}

	/**
	 * Populates the given caches with their initial data, referenced by the data cached for followees.
	 * The algorithm attempts to minimize the impact of corrupt data within followees, meaning it will still return
	 * partial data, even if some of that couldn't be interpreted.
	 * 
	 * @param access Read-access to the network and data structures.
	 * @param cacheUpdater The cache updater.
	 * @param followees The information cached about the followees.
	 * @throws IpfsConnectionException There was a problem accessing the local node.
	 */
	public static void populateInitialCacheForFollowees(IReadingAccess access
			, CacheUpdater cacheUpdater
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
				cacheUpdater.addedFollowee(future.publicKey, description);
			}
		}
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
				_populateElementMapFromFolloweeUserRoot(access, cacheUpdater, future.publicKey, elementsCachedForUser, followeeRecordsElt, followees.getNextBackwardRecord(future.publicKey));
			}
		}
	}


	private static void _populateElementMapFromLocalUserRoot(IReadingAccess access, CacheUpdater cacheUpdater, IpfsKey ourPublicKey, AbstractRecords records) throws IpfsConnectionException
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
			cacheUpdater.addedHomeUserPost(ourPublicKey, cid, record);
		}
	}

	private static void _populateElementMapFromFolloweeUserRoot(IReadingAccess access, CacheUpdater cacheUpdater, IpfsKey followeeKey, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, AbstractRecords records, IpfsFile nextBackwardRecord) throws IpfsConnectionException
	{
		// We want to distinguish between records which are cached for this user and which ones aren't.
		// (in theory, multiple users could have an identical element only cached in some of them which could be
		//  displayed for all of them - we will currently ignore that case and only add the last entry).
		List<IpfsFile> cids = records.getRecordList();
		List<FutureRead<AbstractRecord>> loads = new ArrayList<>();
		// Note that we will limit this list if the followee is being incrementally synchronized, still.
		boolean isWaitingForBackwardMatch = (null != nextBackwardRecord);
		for (IpfsFile cid : cids)
		{
			if (isWaitingForBackwardMatch)
			{
				// This isn't something we have fetched so just notify that we have observed it and move on.
				cacheUpdater.newFolloweePostObserved(followeeKey
						, cid
						, 0L
				);
				// See if this is the first one we want to synchronize (it will be the last one we can't fetch).
				isWaitingForBackwardMatch = !nextBackwardRecord.equals(cid);
			}
			else
			{
				// Make sure that this isn't too big (we could adapt the checker for this, since it isn't pinned, but this is more explicit).
				if (access.getSizeInBytes(cid).get() < AbstractRecord.SIZE_LIMIT_BYTES)
				{
					FutureRead<AbstractRecord> future = access.loadCached(cid, AbstractRecord.DESERIALIZER);
					loads.add(future);
				}
			}
		}
		Iterator<IpfsFile> cidIterator = cids.iterator();
		for (FutureRead<AbstractRecord> future : loads)
		{
			IpfsFile cid = cidIterator.next();
			try
			{
				AbstractRecord record = future.get();
				cacheUpdater.newFolloweePostObserved(followeeKey
						, cid
						, record.getPublishedSecondsUtc()
				);
				_fetchDataForFolloweeElement(access, cacheUpdater, followeeKey, elementsCachedForUser, record, cid);
			}
			catch (FailedDeserializationException e)
			{
				// We already pinned this so it can't fail deserialization.
				throw Assert.unexpected(e);
			}
		}
	}

	private static void _fetchDataForFolloweeElement(IReadingAccess access, CacheUpdater cacheUpdater, IpfsKey followeeKey, Map<IpfsFile, FollowingCacheElement> elementsCachedForUser, AbstractRecord record, IpfsFile cid) throws IpfsConnectionException, FailedDeserializationException
	{
		int videoEdgeSize = 0;
		FollowingCacheElement cachedElement = elementsCachedForUser.get(cid);
		// We only take this path if we know something about this element.
		Assert.assertTrue(null != cachedElement);
		IpfsFile cachedImageOrNull = cachedElement.imageHash();
		IpfsFile cachedAudioOrNull = null;
		IpfsFile cachedVideoOrNull = null;
		IpfsFile leafCid = cachedElement.leafHash();
		if (null != leafCid)
		{
			List<AbstractRecord.Leaf> elements = record.getVideoExtension();
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
				cachedVideoOrNull = leafCid;
				videoEdgeSize = maxEdge;
			}
			else if (leafMime.startsWith("audio/"))
			{
				cachedAudioOrNull = leafCid;
			}
			else
			{
				// We don't currently pin anything like this.
				throw Assert.unreachable();
			}
		}
		cacheUpdater.cachedFolloweePost(followeeKey
				, cid
				, record
				, cachedImageOrNull
				, cachedAudioOrNull
				, cachedVideoOrNull
				, videoEdgeSize
		);
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


	private static record FutureKey<T>(IpfsKey publicKey, FutureRead<T> future)
	{
	}
}
