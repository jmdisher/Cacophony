package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StringHelpers;


/**
 * This contains the common helpers for how to refresh a followee.
 * Note that the design is intended to work for all three cases:
 * 1) Start following user
 * 2) Refresh followed user
 * 3) Stop following user
 * The implementation assumes that it can grow the followee cache to 100% of its target size and that shrink operations
 * will happen elsewhere, independently.
 * The implementation will pin meta-data and leaf files as it sees fit:
 * -generally, all meta-data (including record elements which aren't too big) is pinned (since these are small)
 * -meta-data which is too big to pin is ignored (and will never be pinned)
 * -a failure to pin meta-data (refusing to pin big meta-data is NOT a failure) will result in abandoning the entire
 *  refresh operation with IpfsConnectionException.  Note that this will NOT unpin anything, in this case, assuming it
 *  will be retried later.
 * -a failure to pin leaf data will NOT cause the refresh operation to fail, but will just mean that element will not be
 *  considered part of the cache (and its leaf elements will be explicitly unpinned)
 * -elements we choose to explicitly cache may or may not actually include leaf elements
 * The general design of this algorithm is that it shouldn't need to reach into other components or explicitly
 * read/write any local storage.
 * TODO:  Treat all records without leaves as being part of the cache, and never evict them as they are tiny.
 */
public class FolloweeRefreshLogic
{
	/**
	 * Performs a refresh of the cached elements referenced by the given indices.  It can be used to start following,
	 * refresh an existing followee, and stop following a given user.
	 * 
	 * @param support The interface of external requirements used by the algorithm.
	 * @param globalPrefs The global preferences object (used for leaf selection and cache limit checks).
	 * @param existingCacheElements The cache elements which are already cached for this user from previous calls.
	 * @param oldIndexElement The previous index of the user, from the last refresh attempt.
	 * @param newIndexElement The new index of the user, to be used for this refresh attempt.
	 * @param currentCacheUsageInBytes The current cache occupancy.
	 * @return The new list of cache elements for this user (replaces the old, not adding to it).
	 * @throws IpfsConnectionException If there is a failure to fetch a meta-data element (means an abort).
	 * @throws SizeConstraintException If a meta-data element is too big for our limits (means an abort).
	 */
	public static FollowingCacheElement[] refreshFollowee(IRefreshSupport support
			, GlobalPrefs globalPrefs
			, FollowingCacheElement[] existingCacheElements
			, IpfsFile oldIndexElement
			, IpfsFile newIndexElement
			, long currentCacheUsageInBytes
	) throws IpfsConnectionException, SizeConstraintException
	{
		// Note that only the roots can be null (at most one).
		// Even the existingRecord will be non-null, even if nothing is in it.
		Assert.assertTrue(null != support);
		Assert.assertTrue(null != globalPrefs);
		Assert.assertTrue(null != existingCacheElements);
		Assert.assertTrue((null != oldIndexElement) || (null != newIndexElement));
		
		// Check if the root changed.
		FollowingCacheElement[] updatedCacheElements = null;
		if ((null == oldIndexElement) || !oldIndexElement.equals(newIndexElement))
		{
			// In case of failure and a need to retry, we want to pin everything before we unpin anything.
			List<IpfsFile> metaDataToUnpin = new ArrayList<>();
			List<IpfsFile> filesToUnpin = new ArrayList<>();
			
			if (null != oldIndexElement)
			{
				metaDataToUnpin.add(oldIndexElement);
			}
			if (null != newIndexElement)
			{
				// Make sure that this isn't too big.
				long indexSize = support.getSizeInBytes(newIndexElement).get();
				if (indexSize > SizeLimits.MAX_INDEX_SIZE_BYTES)
				{
					throw new SizeConstraintException("index", indexSize, SizeLimits.MAX_INDEX_SIZE_BYTES);
				}
				
				// Add it to the cache before we proceed.
				support.addMetaDataToFollowCache(newIndexElement).get();
			}
			StreamIndex oldIndex = _loadIndex(support, oldIndexElement);
			StreamIndex newIndex = _loadIndex(support, newIndexElement);
			
			IpfsFile oldDescriptionElement = _cidOrNull(oldIndex.getDescription());
			IpfsFile newDescriptionElement = _cidOrNull(newIndex.getDescription());
			_refreshDescription(support, metaDataToUnpin, oldDescriptionElement, newDescriptionElement);
			
			IpfsFile oldRecommendationsElement = _cidOrNull(oldIndex.getRecommendations());
			IpfsFile newRecommendationsElement = _cidOrNull(newIndex.getRecommendations());
			_refreshRecommendations(support, metaDataToUnpin, oldRecommendationsElement, newRecommendationsElement);
			
			IpfsFile oldRecordsElement = _cidOrNull(oldIndex.getRecords());
			IpfsFile newRecordsElement = _cidOrNull(newIndex.getRecords());
			updatedCacheElements = _refreshRecords(support, globalPrefs, existingCacheElements, metaDataToUnpin, filesToUnpin, oldRecordsElement, newRecordsElement, currentCacheUsageInBytes);
			
			// Drain all the records of whatever we want to unpin, now that everything is working.
			for (IpfsFile cid : metaDataToUnpin)
			{
				support.removeMetaDataFromFollowCache(cid);
			}
			for (IpfsFile cid : filesToUnpin)
			{
				support.removeFileFromFollowCache(cid);
			}
		}
		else
		{
			// Nothing changed so just return the original.
			updatedCacheElements = existingCacheElements;
		}
		return updatedCacheElements;
	}


	private static void _refreshDescription(IRefreshSupport support
			, List<IpfsFile> metaDataToUnpin
			, IpfsFile oldDescriptionElement
			, IpfsFile newDescriptionElement
	) throws IpfsConnectionException
	{
		// Check if the root changed.
		if ((null == oldDescriptionElement) || !oldDescriptionElement.equals(newDescriptionElement))
		{
			if (null != oldDescriptionElement)
			{
				metaDataToUnpin.add(oldDescriptionElement);
				StreamDescription oldDescription = support.loadCached(oldDescriptionElement, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
				// The descriptions always contain a picture reference (often the default but never nothing) which we cache as meta-data.
				metaDataToUnpin.add(IpfsFile.fromIpfsCid(oldDescription.getPicture()));
			}
			if (null != newDescriptionElement)
			{
				support.addMetaDataToFollowCache(newDescriptionElement).get();
				StreamDescription newDescription = support.loadCached(newDescriptionElement, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
				// The descriptions always contain a picture reference (often the default but never nothing) which we cache as meta-data.
				support.addMetaDataToFollowCache(IpfsFile.fromIpfsCid(newDescription.getPicture())).get();
			}
		}
	}

	private static void _refreshRecommendations(IRefreshSupport support
			, List<IpfsFile> metaDataToUnpin
			, IpfsFile oldRecommendationsElement
			, IpfsFile newRecommendationsElement
	) throws IpfsConnectionException
	{
		// Check if the root changed.
		if ((null == oldRecommendationsElement) || !oldRecommendationsElement.equals(newRecommendationsElement))
		{
			StreamRecommendations oldRecommendations = null;
			StreamRecommendations newRecommendations = null;
			if (null != oldRecommendationsElement)
			{
				metaDataToUnpin.add(oldRecommendationsElement);
				oldRecommendations = support.loadCached(oldRecommendationsElement, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
			}
			if (null != newRecommendationsElement)
			{
				support.addMetaDataToFollowCache(newRecommendationsElement).get();
				newRecommendations = support.loadCached(newRecommendationsElement, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
			}
			
			// The recommendations have nothing else to cache so we just make sure that they were consistently loaded (since we wanted to prove they could be parsed correctly).
			Assert.assertTrue((null == oldRecommendationsElement) == (null == oldRecommendations));
			Assert.assertTrue((null == newRecommendationsElement) == (null == newRecommendations));
		}
	}

	private static FollowingCacheElement[] _refreshRecords(IRefreshSupport support
			, GlobalPrefs globalPrefs
			, FollowingCacheElement[] initialElements
			, List<IpfsFile> metaDataToUnpin
			, List<IpfsFile> filesToUnpin
			, IpfsFile oldRecordsElement
			, IpfsFile newRecordsElement
			, long currentCacheUsageInBytes
	) throws IpfsConnectionException
	{
		FollowingCacheElement[] finalElements = null;
		// Check if the root changed.
		if ((null == oldRecordsElement) || !oldRecordsElement.equals(newRecordsElement))
		{
			if (null != oldRecordsElement)
			{
				metaDataToUnpin.add(oldRecordsElement);
			}
			if (null != newRecordsElement)
			{
				support.addMetaDataToFollowCache(newRecordsElement).get();
			}
			StreamRecords oldRecords = _loadRecords(support, oldRecordsElement);
			StreamRecords newRecords = _loadRecords(support, newRecordsElement);
			
			// The records is the complex case since we need to walk the record lists, compare them (since it may have
			// grown or shrunk) and then determine what actual data to cache from within the records.
			Set<IpfsFile> oldRecordSet = oldRecords.getRecord().stream().map((String raw) -> IpfsFile.fromIpfsCid(raw)).collect(Collectors.toSet());
			List<IpfsFile> newRecordList = newRecords.getRecord().stream().map((String raw) -> IpfsFile.fromIpfsCid(raw)).collect(Collectors.toList());
			Set<IpfsFile> removedRecords = new HashSet<>();
			removedRecords.addAll(oldRecordSet);
			removedRecords.removeAll(newRecordList);
			Map<IpfsFile, FollowingCacheElement> recordsWithCachedLeaves = List.of(initialElements).stream().collect(Collectors.toMap((FollowingCacheElement elt) -> elt.elementHash(), (FollowingCacheElement elt) -> elt));
			
			// Process the removed set, adding them to the meta-data to unpin collection and adding any cached leaves to the files to unpin collection.
			for (IpfsFile removedRecord : removedRecords)
			{
				metaDataToUnpin.add(removedRecord);
				FollowingCacheElement elt = recordsWithCachedLeaves.get(removedRecord);
				if (null != elt)
				{
					if (null != elt.imageHash())
					{
						filesToUnpin.add(elt.imageHash());
					}
					if (null != elt.leafHash())
					{
						filesToUnpin.add(elt.leafHash());
					}
				}
			}
			
			// Walk the new record list to create our final FollowingCacheElement list:
			// -adding any existing FollowingCacheElement
			// -process any new records to pin them and decide if we should pin their leaf elements
			List<FollowingCacheElement> finalList = new ArrayList<>();
			List<RawElementData> newRecordsBeingProcessedInitial = new ArrayList<>();
			for (IpfsFile currentRecord : newRecordList)
			{
				if (oldRecordSet.contains(currentRecord))
				{
					// This is a record which is staying.  Check to see if there is an existing cache element.
					FollowingCacheElement elt = recordsWithCachedLeaves.get(currentRecord);
					if (null != elt)
					{
						finalList.add(elt);
					}
				}
				else
				{
					// This is a fully new record so check its size is acceptable.
					RawElementData data = new RawElementData();
					data.elementCid = currentRecord;
					data.futureSize = support.getSizeInBytes(currentRecord);
					newRecordsBeingProcessedInitial.add(data);
				}
			}
			
			// Now, wait for all the sizes to come back and only pin elements which are below our size threshold.
			List<RawElementData> newRecordsBeingProcessedSizeChecked = new ArrayList<>();
			for (RawElementData data : newRecordsBeingProcessedInitial)
			{
				// A connection exception here will cause refresh to fail.
				data.size = data.futureSize.get();
				// If this element is too big, we won't pin it or consider caching it (this is NOT refresh failure).
				// Note that this means any path which directly reads this element should check the size to see if it is present.
				if (data.size <= SizeLimits.MAX_RECORD_SIZE_BYTES)
				{
					data.futureElementPin = support.addMetaDataToFollowCache(data.elementCid);
					newRecordsBeingProcessedSizeChecked.add(data);
				}
				else
				{
					support.logMessage("Record entry for " + data.elementCid + " is too big (" + StringHelpers.humanReadableBytes(data.size) + "): Ignoring this entry (will also be ignored in the future)");
				}
			}
			newRecordsBeingProcessedInitial = null;
			
			// Now, wait for all the pins of the elements and check the sizes of their leaves.
			List<RawElementData> newRecordsBeingProcessedCalculatingLeaves = new ArrayList<>();
			for (RawElementData data : newRecordsBeingProcessedSizeChecked)
			{
				// A connection exception here will cause refresh to fail.
				data.futureElementPin.get();
				data.futureElementPin = null;
				// We pinned this so the read should be pretty-well instantaneous.
				StreamRecord record = support.loadCached(data.elementCid, (byte[] raw) -> GlobalData.deserializeRecord(raw)).get();
				// We will decide on what leaves to pin, but we will still decide to cache this even if there aren't any leaves.
				_selectLeavesForElement(support, data, record, globalPrefs.videoEdgePixelMax());
				newRecordsBeingProcessedCalculatingLeaves.add(data);
			}
			newRecordsBeingProcessedSizeChecked = null;
			
			// Now, we wait for the sizes to come back and then choose which elements to cache.
			List<CacheAlgorithm.Candidate<RawElementData>> candidates = new ArrayList<>();
			for (RawElementData data : newRecordsBeingProcessedCalculatingLeaves)
			{
				boolean bothLoaded = true;
				long byteSize = 0L;
				// NOTE:  If we fail to check sizes or pin any of the leaves, we will NOT cache this element but this is NOT a refresh failure.
				if (null != data.thumbnailSizeFuture)
				{
					try
					{
						data.thumbnailSize = data.thumbnailSizeFuture.get();
					}
					catch (IpfsConnectionException e)
					{
						bothLoaded = false;
						support.logMessage("Failed to load size for thumbnail for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
					data.thumbnailSizeFuture = null;
					byteSize += data.thumbnailSize;
				}
				if (null != data.videoSizeFuture)
				{
					try
					{
						data.videoSize = data.videoSizeFuture.get();
					}
					catch (IpfsConnectionException e)
					{
						bothLoaded = false;
						support.logMessage("Failed to load size for video for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
					data.videoSizeFuture = null;
					byteSize += data.videoSize;
				}
				// We will only consider this a successful cache operation if all leaf elements' sizes were fetched.
				if (bothLoaded)
				{
					CacheAlgorithm.Candidate<RawElementData> candidate = new CacheAlgorithm.Candidate<RawElementData>(byteSize, data);
					// NOTE:  The last element in the list is the most recent, so we want to prioritize that.
					candidates.add(0, candidate);
				}
			}
			newRecordsBeingProcessedCalculatingLeaves = null;
			
			List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = _selectCandidatesForAddition(globalPrefs, currentCacheUsageInBytes, candidates);
			candidates = null;
			
			// We can now walk the final selection and pin all the relevant elements.
			List<RawElementData> candidatesBeingPinned = new ArrayList<>();
			for (CacheAlgorithm.Candidate<RawElementData> candidate : finalSelection)
			{
				RawElementData data = candidate.data();
				if (null != data.thumbnailHash)
				{
					data.futureThumbnailPin = support.addFileToFollowCache(data.thumbnailHash);
				}
				if (null != data.videoHash)
				{
					data.futureVideoPin = support.addFileToFollowCache(data.videoHash);
				}
				candidatesBeingPinned.add(data);
			}
			
			// Finally, walk the records whose leaves we pinned and build FollowingCacheElement instances for each.
			for (RawElementData data : candidatesBeingPinned)
			{
				boolean shouldProceed = true;
				if (null != data.futureThumbnailPin)
				{
					try
					{
						data.futureThumbnailPin.get();
					}
					catch (IpfsConnectionException e)
					{
						// We failed the pin so drop this element.
						shouldProceed = false;
						data.thumbnailHash = null;
						support.logMessage("Failed to pin thumbnail for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
				}
				if (null != data.futureVideoPin)
				{
					try
					{
						data.futureVideoPin.get();
					}
					catch (IpfsConnectionException e)
					{
						// We failed the pin so drop this element.
						shouldProceed = false;
						data.videoHash = null;
						support.logMessage("Failed to pin video for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
				}
				// We will only proceed to add this to the cache if everything was pinned.
				if (shouldProceed)
				{
					FollowingCacheElement element = new FollowingCacheElement(data.elementCid, data.thumbnailHash, data.videoHash, data.thumbnailSize + data.videoSize);
					finalList.add(element);
					support.logMessage("Successfully pinned " + data.elementCid + "!");
					if (null != data.thumbnailHash)
					{
						support.logMessage("\t-thumnail " + data.thumbnailHash + " (" + StringHelpers.humanReadableBytes(data.thumbnailSize) + ")");
					}
					if (null != data.videoHash)
					{
						support.logMessage("\t-thumnail " + data.videoHash + " (" + StringHelpers.humanReadableBytes(data.videoSize) + ")");
					}
				}
				else
				{
					// We may have only partially failed so see which we may need to unpin - ignore the results as this is just best-efforts cleanup.
					if (null != data.thumbnailHash)
					{
						support.removeFileFromFollowCache(data.thumbnailHash);
					}
					if (null != data.videoHash)
					{
						support.removeFileFromFollowCache(data.videoHash);
					}
				}
			}
			finalElements = finalList.toArray(new FollowingCacheElement[finalList.size()]);
		}
		else
		{
			// Nothing is changing.
			finalElements = initialElements;
		}
		return finalElements;
	}

	private static List<CacheAlgorithm.Candidate<RawElementData>> _selectCandidatesForAddition(GlobalPrefs globalPrefs, long currentCacheUsageInBytes, List<CacheAlgorithm.Candidate<RawElementData>> candidates)
	{
		// NOTE:  We always want to add the newest element whether this is a new followee or a refreshed one, so handle that as a special case.
		// Also remember that we need to add this size to the cache since it counts as already being selected.
		// TODO:  Refactor this special logic into some kind of pluggable "cache strategy" for more reliable testing and more exotic performance considerations.
		long effectiveCacheUsedBytes = currentCacheUsageInBytes;
		List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = new ArrayList<>();
		if (candidates.size() > 0)
		{
			CacheAlgorithm.Candidate<RawElementData> firstElement = candidates.remove(0);
			effectiveCacheUsedBytes += firstElement.byteSize();
			finalSelection.add(firstElement);
		}
		CacheAlgorithm algorithm = new CacheAlgorithm(globalPrefs.followCacheTargetBytes(), effectiveCacheUsedBytes);
		List<CacheAlgorithm.Candidate<RawElementData>> selected = algorithm.toAddInNewAddition(candidates);
		finalSelection.addAll(selected);
		return finalSelection;
	}

	private static StreamIndex _loadIndex(IRefreshSupport support, IpfsFile element) throws IpfsConnectionException
	{
		return (null != element)
				? support.loadCached(element, (byte[] data) -> GlobalData.deserializeIndex(data)).get()
				: new StreamIndex()
		;
	}

	private static StreamRecords _loadRecords(IRefreshSupport support, IpfsFile element) throws IpfsConnectionException
	{
		return (null != element)
				? support.loadCached(element, (byte[] data) -> GlobalData.deserializeRecords(data)).get()
				: new StreamRecords()
		;
	}

	private static void _selectLeavesForElement(IRefreshSupport support, RawElementData data, StreamRecord record, int videoEdgePixelMax)
	{
		IpfsFile imageHash = null;
		IpfsFile leafHash = null;
		int biggestEdge = 0;
		for (DataElement elt : record.getElements().getElement())
		{
			IpfsFile eltCid = IpfsFile.fromIpfsCid(elt.getCid());
			if (null != elt.getSpecial())
			{
				Assert.assertTrue(null == imageHash);
				imageHash = eltCid;
			}
			else if ((elt.getWidth() >= biggestEdge) && (elt.getWidth() <= videoEdgePixelMax) && (elt.getHeight() >= biggestEdge) && (elt.getHeight() <= videoEdgePixelMax))
			{
				biggestEdge = Math.max(elt.getWidth(), elt.getHeight());
				leafHash = eltCid;
			}
		}
		if (null != imageHash)
		{
			data.thumbnailHash = imageHash;
			data.thumbnailSizeFuture = support.getSizeInBytes(imageHash);
		}
		if (null != leafHash)
		{
			data.videoHash = leafHash;
			data.videoSizeFuture = support.getSizeInBytes(leafHash);
		}
	}

	private static IpfsFile _cidOrNull(String rawCid)
	{
		return (null != rawCid)
				? IpfsFile.fromIpfsCid(rawCid)
				: null
		;
	}

	/**
	 * The interface required for external callers to this class.
	 * This just exists to make the external requirements clearer and to make tests simpler.
	 */
	public interface IRefreshSupport
	{
		void logMessage(String message);
		FutureSize getSizeInBytes(IpfsFile cid);
		FuturePin addMetaDataToFollowCache(IpfsFile cid);
		void removeMetaDataFromFollowCache(IpfsFile cid);
		FuturePin addFileToFollowCache(IpfsFile cid);
		void removeFileFromFollowCache(IpfsFile cid);
		<R> FutureRead<R> loadCached(IpfsFile file, Function<byte[], R> decoder);
	}
}