package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;
import com.jeffdisher.cacophony.utils.SizeLimits;


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
 * -meta-data which is too big to pin (due to size limits) or fails to be parsed (malformed) will cause the refresh to
 *  abort.
 * -a failure to pin meta-data (refusing to pin big meta-data is NOT a failure) will result in abandoning the entire
 *  refresh operation with IpfsConnectionException.  Note that this will NOT unpin anything, in this case, assuming it
 *  will be retried later.
 * -a failure to pin leaf data will NOT cause the refresh operation to fail, but will just mean that element will not be
 *  considered part of the cache (and its leaf elements will be explicitly unpinned)
 * -elements we choose to explicitly cache may or may not actually include leaf elements
 * The general design of this algorithm is that it shouldn't need to reach into other components or explicitly
 * read/write any local storage.
 */
public class FolloweeRefreshLogic
{
	/**
	 * Performs a refresh of the cached elements referenced by the given indices.  It can only be used on existing
	 * followees, to either refresh them or delete them.  For new cases, startFollowing() must be called first.
	 * 
	 * @param support The interface of external requirements used by the algorithm.
	 * @param prefs The preferences object (used for leaf selection and cache limit checks).
	 * @param oldIndexElement The previous index of the user, from the last refresh attempt (cannot be null).
	 * @param newIndexElement The new index of the user, to be used for this refresh attempt.
	 * @param currentCacheUsageInBytes The current cache occupancy.
	 * @throws IpfsConnectionException If there is a failure to fetch a meta-data element (means an abort).
	 * @throws SizeConstraintException If a meta-data element is too big for our limits (means an abort).
	 * @throws FailedDeserializationException Meta-data was considered invalid and couldn't be parsed (means an abort).
	 */
	public static void refreshFollowee(IRefreshSupport support
			, PrefsData prefs
			, IpfsFile oldIndexElement
			, IpfsFile newIndexElement
			, long currentCacheUsageInBytes
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// Note that only the new root can be null.
		Assert.assertTrue(null != support);
		Assert.assertTrue(null != prefs);
		Assert.assertTrue(null != oldIndexElement);
		
		// Check if the root changed.
		if (!oldIndexElement.equals(newIndexElement))
		{
			support.deferredRemoveMetaDataFromFollowCache(oldIndexElement);
			if (null != newIndexElement)
			{
				// Make sure that this isn't too big.
				_checkSizeInline(support, "index", newIndexElement, AbstractIndex.SIZE_LIMIT_BYTES);
				
				// Add it to the cache before we proceed.
				support.addMetaDataToFollowCache(newIndexElement).get();
			}
			AbstractIndex oldIndex = _loadIndex(support, oldIndexElement);
			AbstractIndex newIndex = _loadIndex(support, newIndexElement);
			
			IpfsFile oldDescriptionElement = oldIndex.descriptionCid;
			IpfsFile newDescriptionElement = newIndex.descriptionCid;
			_refreshDescription(support, oldDescriptionElement, newDescriptionElement);
			
			IpfsFile oldRecommendationsElement = oldIndex.recommendationsCid;
			IpfsFile newRecommendationsElement = newIndex.recommendationsCid;
			_refreshRecommendations(support, oldRecommendationsElement, newRecommendationsElement);
			
			IpfsFile oldRecordsElement = oldIndex.recordsCid;
			IpfsFile newRecordsElement = newIndex.recordsCid;
			_refreshRecords(support, prefs, oldRecordsElement, newRecordsElement, currentCacheUsageInBytes);
		}
	}

	/**
	 * Handles the case when a new followee is being added to the followee set, for the first time.  This only validates
	 * the core meta-data and caches their description and recommendation information, ignoring their record list and
	 * uploading a fake empty list so that this "first refresh" can complete very quickly.
	 * This allows a new followee to be added quickly, while the long-running refresh operation can be handled in a more
	 * forgiving path.
	 * 
	 * @param support The interface of external requirements used by the algorithm.
	 * @param newIndexElement The new index of the user, to be used for this refresh attempt.
	 * @return The "fake" index element CID created to allow this to complete quickly.
	 * @throws IpfsConnectionException If there is a failure to fetch a meta-data element (means an abort).
	 * @throws SizeConstraintException If a meta-data element is too big for our limits (means an abort).
	 * @throws FailedDeserializationException Meta-data was considered invalid and couldn't be parsed (means an abort).
	 */
	public static IpfsFile startFollowing(IStartSupport support
			, IpfsFile newIndexElement
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// Note that only the roots can be null (at most one).
		// Even the existingRecord will be non-null, even if nothing is in it.
		Assert.assertTrue(null != support);
		Assert.assertTrue(null != newIndexElement);
		
		// Load the root element - we will NOT cache this, since we are going to use a hacked variant.
		// (since we are using the not-cached loader, it will do our size check for us)
		AbstractIndex newIndex = support.loadNotCached(newIndexElement, "index", AbstractIndex.SIZE_LIMIT_BYTES, AbstractIndex.DESERIALIZER).get();
		
		// Load the description.
		IpfsFile newDescriptionElement = newIndex.descriptionCid;
		_checkSizeInline(support, "description", newDescriptionElement, AbstractDescription.SIZE_LIMIT_BYTES);
		support.addMetaDataToFollowCache(newDescriptionElement).get();
		AbstractDescription newDescription = support.loadCached(newDescriptionElement, AbstractDescription.DESERIALIZER).get();
		IpfsFile userPicCid = newDescription.getPicCid();
		_checkSizeInline(support, "userpic", userPicCid, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
		support.addMetaDataToFollowCache(userPicCid).get();
		
		// Load the recommendations.
		IpfsFile newRecommendationsElement = newIndex.recommendationsCid;
		_checkSizeInline(support, "recommendations", newRecommendationsElement, SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
		support.addMetaDataToFollowCache(newRecommendationsElement).get();
		AbstractRecommendations newRecommendations = support.loadCached(newRecommendationsElement, AbstractRecommendations.DESERIALIZER).get();
		Assert.assertTrue(null != newRecommendations);
		
		// Notify the support that this user is either new or has changed its description.
		support.followeeDescriptionNewOrUpdated(newDescription.getName(), newDescription.getDescription(), userPicCid, newDescription.getEmail(), newDescription.getWebsite());
		
		// (we ignore the records element and create a fake one - this allows the expensive part of the refresh to be decoupled from the initial follow).
		AbstractRecords fakeRecords = AbstractRecords.createNew();
		IpfsFile fakeRecordsCid = support.uploadNewData(fakeRecords.serializeV1());
		AbstractIndex fakeIndex = AbstractIndex.createNew();
		fakeIndex.descriptionCid = newDescriptionElement;
		fakeIndex.recommendationsCid = newRecommendationsElement;
		fakeIndex.recordsCid = fakeRecordsCid;
		IpfsFile fakeIndexCid = support.uploadNewData(fakeIndex.serializeV1());
		
		return fakeIndexCid;
	}


	private static void _refreshDescription(IRefreshSupport support
			, IpfsFile oldDescriptionElement
			, IpfsFile newDescriptionElement
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// Check if the root changed.
		if ((null == oldDescriptionElement) || !oldDescriptionElement.equals(newDescriptionElement))
		{
			if (null != oldDescriptionElement)
			{
				support.deferredRemoveMetaDataFromFollowCache(oldDescriptionElement);
				AbstractDescription oldDescription = support.loadCached(oldDescriptionElement, AbstractDescription.DESERIALIZER).get();
				// The descriptions always contain a picture reference (often the default but never nothing) which we cache as meta-data.
				support.deferredRemoveMetaDataFromFollowCache(oldDescription.getPicCid());
			}
			if (null != newDescriptionElement)
			{
				// Make sure that this isn't too big.
				_checkSizeInline(support, "description", newDescriptionElement, AbstractDescription.SIZE_LIMIT_BYTES);
				
				support.addMetaDataToFollowCache(newDescriptionElement).get();
				AbstractDescription newDescription = support.loadCached(newDescriptionElement, AbstractDescription.DESERIALIZER).get();
				
				// The descriptions always contain a picture reference (often the default but never nothing) which we cache as meta-data.
				// Make sure that this isn't too big.
				IpfsFile userPicCid = newDescription.getPicCid();
				_checkSizeInline(support, "userpic", userPicCid, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
				support.addMetaDataToFollowCache(userPicCid).get();
				
				// Notify the support that this user is either new or has changed its description.
				support.followeeDescriptionNewOrUpdated(newDescription.getName(), newDescription.getDescription(), userPicCid, newDescription.getEmail(), newDescription.getWebsite());
			}
		}
	}

	private static void _refreshRecommendations(IRefreshSupport support
			, IpfsFile oldRecommendationsElement
			, IpfsFile newRecommendationsElement
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// Check if the root changed.
		if ((null == oldRecommendationsElement) || !oldRecommendationsElement.equals(newRecommendationsElement))
		{
			AbstractRecommendations oldRecommendations = null;
			AbstractRecommendations newRecommendations = null;
			if (null != oldRecommendationsElement)
			{
				support.deferredRemoveMetaDataFromFollowCache(oldRecommendationsElement);
				oldRecommendations = support.loadCached(oldRecommendationsElement, AbstractRecommendations.DESERIALIZER).get();
			}
			if (null != newRecommendationsElement)
			{
				// Make sure that this isn't too big.
				_checkSizeInline(support, "recommendations", newRecommendationsElement, SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
				
				support.addMetaDataToFollowCache(newRecommendationsElement).get();
				newRecommendations = support.loadCached(newRecommendationsElement, AbstractRecommendations.DESERIALIZER).get();
			}
			
			// The recommendations have nothing else to cache so we just make sure that they were consistently loaded (since we wanted to prove they could be parsed correctly).
			Assert.assertTrue((null == oldRecommendationsElement) == (null == oldRecommendations));
			Assert.assertTrue((null == newRecommendationsElement) == (null == newRecommendations));
		}
	}

	private static void _refreshRecords(IRefreshSupport support
			, PrefsData prefs
			, IpfsFile oldRecordsElement
			, IpfsFile newRecordsElement
			, long currentCacheUsageInBytes
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// Check if the root changed.
		if ((null == oldRecordsElement) || !oldRecordsElement.equals(newRecordsElement))
		{
			if (null != oldRecordsElement)
			{
				support.deferredRemoveMetaDataFromFollowCache(oldRecordsElement);
			}
			if (null != newRecordsElement)
			{
				// Make sure that this isn't too big.
				_checkSizeInline(support, "records", newRecordsElement, AbstractRecords.SIZE_LIMIT_BYTES);
				
				support.addMetaDataToFollowCache(newRecordsElement).get();
			}
			AbstractRecords oldRecords = _loadRecords(support, oldRecordsElement);
			AbstractRecords newRecords = _loadRecords(support, newRecordsElement);
			
			// The records is the complex case since we need to walk the record lists, compare them (since it may have
			// grown or shrunk) and then determine what actual data to cache from within the records.
			Set<IpfsFile> oldRecordSet = oldRecords.getRecordList().stream().collect(Collectors.toSet());
			List<IpfsFile> newRecordList = newRecords.getRecordList().stream().collect(Collectors.toList());
			Set<IpfsFile> removedRecords = new HashSet<>();
			removedRecords.addAll(oldRecordSet);
			removedRecords.removeAll(newRecordList);
			
			// Process the removed set, adding them to the meta-data to unpin collection and adding any cached leaves to the files to unpin collection.
			for (IpfsFile removedRecord : removedRecords)
			{
				support.deferredRemoveMetaDataFromFollowCache(removedRecord);
				IpfsFile imageHash = support.getImageForCachedElement(removedRecord);
				if (null != imageHash)
				{
					support.deferredRemoveFileFromFollowCache(imageHash);
				}
				IpfsFile leafHash = support.getLeafForCachedElement(removedRecord);
				if (null != leafHash)
				{
					support.deferredRemoveFileFromFollowCache(leafHash);
				}
				support.removeElementFromCache(removedRecord);
			}
			
			// Walk the new record list to create our final FollowingCacheElement list:
			// -adding any existing FollowingCacheElement
			// -process any new records to pin them and decide if we should pin their leaf elements
			List<RawElementData> newRecordsBeingProcessedInitial = new ArrayList<>();
			for (IpfsFile currentRecord : newRecordList)
			{
				if (oldRecordSet.contains(currentRecord))
				{
					// This is a record which is staying so leave its pinned elements and whether or not we cached it unchanged.
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
			support.logMessage("Checking sizes of new records (checking " + newRecordsBeingProcessedInitial.size() + " records)...");
			for (RawElementData data : newRecordsBeingProcessedInitial)
			{
				// A connection exception here will cause refresh to fail.
				data.size = data.futureSize.get();
				// If this element is too big, we won't pin it or consider caching it (this is NOT refresh failure).
				// Note that this means any path which directly reads this element should check the size to see if it is present.
				if (data.size <= AbstractRecord.SIZE_LIMIT_BYTES)
				{
					data.futureElementPin = support.addMetaDataToFollowCache(data.elementCid);
					newRecordsBeingProcessedSizeChecked.add(data);
				}
				else
				{
					throw new SizeConstraintException("record", data.size, AbstractRecord.SIZE_LIMIT_BYTES);
				}
			}
			newRecordsBeingProcessedInitial = null;
			
			// Now, wait for all the pins of the elements and check the sizes of their leaves.
			List<RawElementData> newRecordsBeingProcessedCalculatingLeaves = new ArrayList<>();
			support.logMessage("Waiting for meta-data to be pinned (pinning " + newRecordsBeingProcessedSizeChecked.size() + " records)...");
			for (RawElementData data : newRecordsBeingProcessedSizeChecked)
			{
				// A connection exception here will cause refresh to fail.
				data.futureElementPin.get();
				data.futureElementPin = null;
				// We pinned this so the read should be pretty-well instantaneous.
				data.record = support.loadCached(data.elementCid, AbstractRecord.DESERIALIZER).get();
				// Report that we pinned it.
				support.newElementPinned(data.elementCid, data.record.getName(), data.record.getDescription(), data.record.getPublishedSecondsUtc(), data.record.getDiscussionUrl(), data.record.getPublisherKey(), data.record.getExternalElementCount());
				// We will decide on what leaves to pin, but we will still decide to cache this even if there aren't any leaves.
				_selectLeavesForElement(support, data, data.record, prefs.videoEdgePixelMax);
				newRecordsBeingProcessedCalculatingLeaves.add(data);
			}
			newRecordsBeingProcessedSizeChecked = null;
			
			// Now, we wait for the sizes to come back and then choose which elements to cache.
			List<CacheAlgorithm.Candidate<RawElementData>> candidates = new ArrayList<>();
			support.logMessage("Checking sizes of data elements (checking for " + newRecordsBeingProcessedCalculatingLeaves.size() + " records)...");
			for (RawElementData data : newRecordsBeingProcessedCalculatingLeaves)
			{
				boolean bothLoaded = true;
				long byteSize = 0L;
				// NOTE:  If we fail to check sizes or pin any of the leaves, we will NOT cache this element but this is NOT a refresh failure.
				if (null != data.thumbnailSizeFuture)
				{
					try
					{
						data.thumbnailSizeBytes = data.thumbnailSizeFuture.get();
						// Make sure that this isn't over our preference limit.
						if (data.thumbnailSizeBytes > prefs.followeeRecordThumbnailMaxBytes)
						{
							bothLoaded = false;
							support.logMessage("Record " + data.elementCid + " is being skipped since its thumbnail is " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " which is above the prefs limit of " + MiscHelpers.humanReadableBytes(prefs.followeeRecordThumbnailMaxBytes));
						}
					}
					catch (IpfsConnectionException e)
					{
						bothLoaded = false;
						support.logMessage("Failed to load size for thumbnail for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
					data.thumbnailSizeFuture = null;
					byteSize += data.thumbnailSizeBytes;
				}
				if (null != data.leafSizeFuture)
				{
					try
					{
						data.leafSizeBytes = data.leafSizeFuture.get();
						// Make sure that this isn't over our preference limit.
						long relevantSizeBytes = data.leafHash.equals(data.audioLeafHash)
								? prefs.followeeRecordAudioMaxBytes
								: prefs.followeeRecordVideoMaxBytes
						;
						if (data.leafSizeBytes > relevantSizeBytes)
						{
							bothLoaded = false;
							support.logMessage("Record " + data.elementCid + " is being skipped since its leaf is " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " which is above the prefs limit of " + MiscHelpers.humanReadableBytes(relevantSizeBytes));
						}
					}
					catch (IpfsConnectionException e)
					{
						bothLoaded = false;
						support.logMessage("Failed to load size for leaf for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
					data.leafSizeFuture = null;
					byteSize += data.leafSizeBytes;
				}
				// We will only consider this a successful cache operation if all leaf elements' sizes were fetched.
				if (bothLoaded)
				{
					CacheAlgorithm.Candidate<RawElementData> candidate = new CacheAlgorithm.Candidate<RawElementData>(byteSize, data);
					candidates.add(candidate);
				}
			}
			newRecordsBeingProcessedCalculatingLeaves = null;
			
			List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = _selectCandidatesForAddition(prefs, currentCacheUsageInBytes, candidates);
			candidates = null;
			
			// We can now walk the final selection and pin all the relevant elements.
			List<RawElementData> candidatesBeingPinned = new ArrayList<>();
			support.logMessage("Pinning all data elements (selected " + finalSelection.size() + " records)...");
			for (CacheAlgorithm.Candidate<RawElementData> candidate : finalSelection)
			{
				RawElementData data = candidate.data();
				support.logMessage("Pinning " + data.elementCid + "...");
				if (null != data.thumbnailHash)
				{
					support.logMessage("\t-thumbnail " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " (" + data.thumbnailHash + ")...");
					data.futureThumbnailPin = support.addFileToFollowCache(data.thumbnailHash);
				}
				if (null != data.leafHash)
				{
					support.logMessage("\t-leaf " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " (" + data.leafHash + ")...");
					data.futureLeafPin = support.addFileToFollowCache(data.leafHash);
				}
				candidatesBeingPinned.add(data);
			}
			
			// Finally, walk the records whose leaves we pinned and build FollowingCacheElement instances for each.
			support.logMessage("Waiting for all data elements to be pinned (" + candidatesBeingPinned.size() + " records)...");
			for (RawElementData data : candidatesBeingPinned)
			{
				support.logMessage("Waiting for record " + data.elementCid + "...");
				boolean shouldProceed = true;
				boolean hasLeafElements = false;
				if (null != data.futureThumbnailPin)
				{
					hasLeafElements = true;
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
				if (null != data.futureLeafPin)
				{
					hasLeafElements = true;
					try
					{
						data.futureLeafPin.get();
					}
					catch (IpfsConnectionException e)
					{
						// We failed the pin so drop this element.
						shouldProceed = false;
						data.leafHash = null;
						support.logMessage("Failed to pin leaf for " + data.elementCid + ": Ignoring this entry (will also be ignored in the future)");
					}
				}
				// We will only proceed to add this to the cache if everything was pinned and there were leaf elements.
				// (Note that we don't record elements without leaves since we always cache meta-data, anyway)
				if (shouldProceed && hasLeafElements)
				{
					support.logMessage("Successfully pinned " + data.elementCid + "!");
					if (null != data.thumbnailHash)
					{
						support.logMessage("\t-thumnail " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " (" + data.thumbnailHash + ")");
					}
					if (null != data.leafHash)
					{
						support.logMessage("\t-leaf " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " (" + data.leafHash + ")");
					}
					support.addElementToCache(data.elementCid, data.thumbnailHash, data.audioLeafHash, data.videoLeafHash, data.videoEdgeSize, data.thumbnailSizeBytes + data.leafSizeBytes);
				}
				else
				{
					// We may have only partially failed so see which we may need to unpin - ignore the results as this is just best-efforts cleanup.
					if (null != data.thumbnailHash)
					{
						support.deferredRemoveFileFromFollowCache(data.thumbnailHash);
					}
					if (null != data.leafHash)
					{
						support.deferredRemoveFileFromFollowCache(data.leafHash);
					}
				}
			}
		}
	}

	private static List<CacheAlgorithm.Candidate<RawElementData>> _selectCandidatesForAddition(PrefsData prefs, long currentCacheUsageInBytes, List<CacheAlgorithm.Candidate<RawElementData>> oldestFirstCandidates)
	{
		// NOTE:  The list we are given and the list we return should be in the order of how they are specified in the
		// user's meta-data entry, but those are oldest-first.  Since our cache algorithm favours earlier elements, and
		// we want those to be the newest elements, we need to reverse the list of candidates and then reverse the list
		// selected.
		List<CacheAlgorithm.Candidate<RawElementData>> newestFirstCandidates = new ArrayList<>(oldestFirstCandidates);
		Collections.reverse(newestFirstCandidates);
		
		// NOTE:  We always want to add the newest element whether this is a new followee or a refreshed one, so handle that as a special case.
		// Also remember that we need to add this size to the cache since it counts as already being selected.
		// TODO:  Refactor this special logic into some kind of pluggable "cache strategy" for more reliable testing and more exotic performance considerations.
		long effectiveCacheUsedBytes = currentCacheUsageInBytes;
		List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = new ArrayList<>();
		if (newestFirstCandidates.size() > 0)
		{
			CacheAlgorithm.Candidate<RawElementData> firstElement = newestFirstCandidates.remove(0);
			effectiveCacheUsedBytes += firstElement.byteSize();
			finalSelection.add(firstElement);
		}
		CacheAlgorithm algorithm = new CacheAlgorithm(prefs.followCacheTargetBytes, effectiveCacheUsedBytes);
		List<CacheAlgorithm.Candidate<RawElementData>> newestFirstSelected = algorithm.toAddInNewAddition(newestFirstCandidates);
		finalSelection.addAll(newestFirstSelected);
		
		// Reverse the final selection since we want it to be in the canonical oldest-first order.
		Collections.reverse(finalSelection);
		return finalSelection;
	}

	private static AbstractIndex _loadIndex(IRefreshSupport support, IpfsFile element) throws IpfsConnectionException, FailedDeserializationException
	{
		return (null != element)
				? support.loadCached(element, AbstractIndex.DESERIALIZER).get()
				: AbstractIndex.createNew()
		;
	}

	private static AbstractRecords _loadRecords(IRefreshSupport support, IpfsFile element) throws IpfsConnectionException, FailedDeserializationException
	{
		return (null != element)
				? support.loadCached(element, AbstractRecords.DESERIALIZER).get()
				: AbstractRecords.createNew()
		;
	}

	private static void _selectLeavesForElement(IRefreshSupport support, RawElementData data, AbstractRecord record, int videoEdgePixelMax)
	{
		IpfsFile imageHash = null;
		IpfsFile videoHash = null;
		IpfsFile audioHash = null;
		int biggestEdge = 0;
		
		LeafFinder leaves = LeafFinder.parseRecord(record);
		imageHash = leaves.thumbnail;
		audioHash = leaves.audio;
		LeafFinder.VideoLeaf videoLeaf = leaves.largestVideoWithLimit(videoEdgePixelMax);
		if (null != videoLeaf)
		{
			videoHash = videoLeaf.cid();
			biggestEdge = videoLeaf.edgeSize();
		}
		
		// We will prefer the video leaf, if available (although we don't currently have a use-case where there would be both).
		data.videoLeafHash = videoHash;
		data.videoEdgeSize = biggestEdge;
		data.audioLeafHash = audioHash;
		IpfsFile leafHash = (null != videoHash) ? videoHash : audioHash;
		if (null != imageHash)
		{
			data.thumbnailHash = imageHash;
			data.thumbnailSizeFuture = support.getSizeInBytes(imageHash);
		}
		if (null != leafHash)
		{
			data.leafHash = leafHash;
			data.leafSizeFuture = support.getSizeInBytes(leafHash);
		}
	}

	private static void _checkSizeInline(_ICommonSupport support, String context, IpfsFile element, long sizeLimit) throws IpfsConnectionException, SizeConstraintException
	{
		long size = support.getSizeInBytes(element).get();
		if (size > sizeLimit)
		{
			throw new SizeConstraintException(context, size, sizeLimit);
		}
	}


	private interface _ICommonSupport
	{
		/**
		 * Logs an informational message.
		 * 
		 * @param message The message to log.
		 */
		void logMessage(String message);
		/**
		 * Requests the size of a CID entry, in bytes.  This CID may or may not be pinned.
		 * 
		 * @param cid The CID to check.
		 * @return The future size response.
		 */
		FutureSize getSizeInBytes(IpfsFile cid);
		/**
		 * Called when a new followee is being added or an existing followee's user description is updated, so we can
		 * populate any caches.
		 * 
		 * @param name The name.
		 * @param description The description.
		 * @param userPicCid The CID of their user picture.
		 * @param emailOrNull Their E-Mail address (can be null).
		 * @param websiteOrNull Their website (can be null).
		 */
		void followeeDescriptionNewOrUpdated(String name
				, String description
				, IpfsFile userPicCid
				, String emailOrNull
				, String websiteOrNull
		);
	}

	/**
	 * The interface required for external callers to this class.
	 * This just exists to make the external requirements clearer and to make tests simpler.
	 */
	public interface IRefreshSupport extends _ICommonSupport
	{
		/**
		 * Called when a new record been pinned.  This may not be added to the cache, since it may have no leaves or
		 * they may be too large, but the meta-data is now locally pinned.
		 * 
		 * @param elementHash The CID of the meta-data XML.
		 * @param name The name of post.
		 * @param description The description of the post.
		 * @param publishedSecondsUtc The publish time of the post.
		 * @param discussionUrl The discussion URL of the post (maybe null).
		 * @param publisherKey The key of the element publisher.
		 * @param leafReferenceCount The number of attached leaves (thumbnail, audio, videos, etc).
		 */
		void newElementPinned(IpfsFile elementHash, String name, String description, long publishedSecondsUtc, String discussionUrl, IpfsKey publisherKey, int leafReferenceCount);
		/**
		 * Requests that a piece of XML meta-data be pinned locally.  This could be the element or some other
		 * intermediary data.
		 * 
		 * @param cid The CID of the meta-data XML.
		 * @return The future pin response.
		 */
		FuturePin addMetaDataToFollowCache(IpfsFile cid);
		/**
		 * Requests that a piece of meta-data XML be unpinned locally.  Note that this may not have been previously
		 * pinned it was too large.
		 * This is assumed to be "deferred":  Only actually unpinned if the entire operation is a success.
		 * 
		 * @param cid The CID of the meta-data XML.
		 */
		void deferredRemoveMetaDataFromFollowCache(IpfsFile cid);
		/**
		 * Requests that a leaf data file be pinned and added to the local cache.
		 * 
		 * @param cid The CID of the leaf data object.
		 * @return The future pin response.
		 */
		FuturePin addFileToFollowCache(IpfsFile cid);
		/**
		 * Requests that a leaf data element be unpinned locally.  Note that this would have been pinned, previously.
		 * This is assumed to be "deferred":  Only actually unpinned if the entire operation is a success.
		 * 
		 * @param cid The CID of the leaf data object.
		 */
		void deferredRemoveFileFromFollowCache(IpfsFile cid);
		/**
		 * Requests a read of data which is already pinned on the local node.
		 * 
		 * @param <R> The type of object to return.
		 * @param file The CID of the data to read.
		 * @param decoder A deserializer to convert the loaded bytes into the returned R type.
		 * @return The future read response.
		 */
		<R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder);
		/**
		 * Returns the thumbnail image CID for a given elementHash which is already in the cache.
		 * 
		 * @param elementHash The CID of the element meta-data XML.
		 * @return The CID of the thumbnail or null, if one wasn't cached.
		 */
		IpfsFile getImageForCachedElement(IpfsFile elementHash);
		/**
		 * Returns the leaf video CID for a given elementHash which is already in the cache.
		 * 
		 * @param elementHash The CID of the element meta-data XML.
		 * @return The CID of the video or null, if one wasn't cached.
		 */
		IpfsFile getLeafForCachedElement(IpfsFile elementHash);
		/**
		 * Requests that an element be added to the cache.
		 * 
		 * @param elementHash The now-pinned meta-data XML CID.
		 * @param imageHash The now-pinned image data (or null).
		 * @param audioLeaf The now-pinned audio data (or null).
		 * @param videoLeaf The now-pinned video data (or null).
		 * @param videoEdgeSize The edge size of the video (0 if null).
		 * @param combinedSizeBytes The combined size of both the image and video, in bytes.
		 */
		void addElementToCache(IpfsFile elementHash, IpfsFile imageHash, IpfsFile audioLeaf, IpfsFile videoLeaf, int videoEdgeSize, long combinedSizeBytes);
		/**
		 * Called when a the meta-data of a previously-observed element has been enqueued for unpin and should be
		 * dropped.
		 * NOTE:  If this element was too big, we will never have seen a corresponding "newElementPinned" call.
		 * 
		 * @param elementHash The CID of the meta-data XML.
		 */
		void removeElementFromCache(IpfsFile elementHash);
	}

	/**
	 * The interface required for external callers to this class.
	 * This just exists to make the external requirements clearer and to make tests simpler.
	 */
	public interface IStartSupport extends _ICommonSupport
	{
		/**
		 * Requests that a piece of XML meta-data be pinned locally.  This could be the element or some other
		 * intermediary data.
		 * 
		 * @param cid The CID of the meta-data XML.
		 * @return The future pin response.
		 */
		FuturePin addMetaDataToFollowCache(IpfsFile cid);
		/**
		 * Requests a read of data which is already pinned on the local node.
		 * 
		 * @param <R> The type of object to return.
		 * @param file The CID of the data to read.
		 * @param decoder A deserializer to convert the loaded bytes into the returned R type.
		 * @return The future read response.
		 */
		<R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder);
		/**
		 * Loads the given file, decoding it into and instance of R, but assuming that it is NOT pinned on the local node.
		 * 
		 * @param <R> The type of object to return.
		 * @param file The CID of the data to read.
		 * @param context The name to use to describe this, if there is an error.
		 * @param maxSizeInBytes The maximum size of the resource, in bytes, in order for it to be loaded (must be positive).
		 * @param decoder A deserializer to convert the loaded bytes into the returned R type.
		 * @return The future read response.
		 */
		<R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder);
		IpfsFile uploadNewData(byte[] data) throws IpfsConnectionException;
	}
}
