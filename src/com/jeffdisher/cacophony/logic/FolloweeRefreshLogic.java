package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecord.Leaf;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
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
	 * When incrementally synchronizing backward, we will fetch 5 records per increment.
	 */
	public static final int INCREMENTAL_RECORD_COUNT = 5;

	/**
	 * Performs a refresh of the cached elements referenced by the given indices.  It can be used to start following,
	 * refresh an existing followee, and stop following a given user.
	 * 
	 * @param support The interface of external requirements used by the algorithm.
	 * @param prefs The preferences object (used for leaf selection and cache limit checks).
	 * @param oldIndexElement The previous index of the user, from the last refresh attempt.
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
		// Note that only the roots can be null (at most one).
		Assert.assertTrue(null != support);
		Assert.assertTrue(null != prefs);
		Assert.assertTrue((null != oldIndexElement) || (null != newIndexElement));
		
		// Check if the root changed.
		if ((null == oldIndexElement) || !oldIndexElement.equals(newIndexElement))
		{
			if (null != oldIndexElement)
			{
				support.deferredRemoveMetaDataFromFollowCache(oldIndexElement);
			}
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
			
			// Even if we did a normal sync of the user data, if the records didn't change, we will actually try an incremental sync on them.
			IpfsFile oldRecordsElement = oldIndex.recordsCid;
			IpfsFile newRecordsElement = newIndex.recordsCid;
			if (null == oldRecordsElement)
			{
				// First sync - this is the same as the incremental case but we need to pin the records meta-data and select the starting-point.
				_checkSizeInline(support, "records", newRecordsElement, AbstractRecords.SIZE_LIMIT_BYTES);
				support.addMetaDataToFollowCache(newRecordsElement).get();
				
				AbstractRecords newRecords = _loadRecords(support, newRecordsElement);
				List<IpfsFile> recordList = newRecords.getRecordList();
				if (recordList.isEmpty())
				{
					// This is just a degenerate case - we are done since the list is empty.
				}
				else
				{
					// We actually need to do some work so synchronize from the last element, backward.
					IpfsFile nextBackwardSyncRecord = recordList.get(recordList.size() - 1);
					_refreshRecordsBackward(support, prefs, newRecordsElement, nextBackwardSyncRecord, currentCacheUsageInBytes, true);
				}
			}
			else if (oldRecordsElement.equals(newRecordsElement))
			{
				// Nothing changed so check if this requires incremental sync.
				IpfsFile nextBackwardSyncRecord = support.getNextBackwardRecord();
				if (null != nextBackwardSyncRecord)
				{
					_refreshRecordsBackward(support, prefs, newRecordsElement, nextBackwardSyncRecord, currentCacheUsageInBytes, false);
				}
				// TODO: If this is null, we currently just assume we are done but we should go back and retry temporary skips, in that case.
			}
			else
			{
				// Normal forward synchronization - we just proceed as we did before incremental synchronization.
				// Note that this may still be very heavy-weight, if the record list changed a lot since the last attempt, but should cover common use-cases well.
				IpfsFile nextBackwardSyncRecord = support.getNextBackwardRecord();
				_refreshRecordsForward(support, prefs, oldRecordsElement, newRecordsElement, nextBackwardSyncRecord, currentCacheUsageInBytes);
			}
		}
		else
		{
			// See if we need to continue an incremental sync.
			IpfsFile nextBackwardSyncRecord = support.getNextBackwardRecord();
			// TODO: If this is null, we currently just assume we are done but we should go back and retry temporary skips, in that case.
			
			// If we have some work to do, do it (otherwise, we are done).
			if (null != nextBackwardSyncRecord)
			{
				AbstractIndex newIndex = _loadIndex(support, newIndexElement);
				IpfsFile newRecordsElement = newIndex.recordsCid;
				_refreshRecordsBackward(support, prefs, newRecordsElement, nextBackwardSyncRecord, currentCacheUsageInBytes, false);
			}
		}
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
				// If the user pic exists (optional in V2), then handle that as meta-data.
				IpfsFile userPicCid = oldDescription.getPicCid();
				if (null != userPicCid)
				{
					support.deferredRemoveMetaDataFromFollowCache(userPicCid);
				}
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
				if (null != userPicCid)
				{
					_checkSizeInline(support, "userpic", userPicCid, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
					support.addMetaDataToFollowCache(userPicCid).get();
				}
				
				// Notify the support that this user is either new or has changed its description.
				support.followeeDescriptionNewOrUpdated(newDescription);
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

	// NOTE:  Refreshing forward can fail with exceptions since that isn't expected.
	private static void _refreshRecordsForward(IRefreshSupport support
			, PrefsData prefs
			, IpfsFile oldRecordsElement
			, IpfsFile newRecordsElement
			, IpfsFile nextBackwardSyncRecord
			, long currentCacheUsageInBytes
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// In this case, we already made sure this isn't the first call.
		Assert.assertTrue(null != oldRecordsElement);
		support.deferredRemoveMetaDataFromFollowCache(oldRecordsElement);
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
		
		// If there is an incremental sync in progress, we need to create a set of records which have been pinned.
		Set<IpfsFile> pinnedRecords = new HashSet<>();
		pinnedRecords.addAll(oldRecordSet);
		if (null != nextBackwardSyncRecord)
		{
			ListIterator<IpfsFile> iterator = _prepareBackwardIterator(oldRecords, nextBackwardSyncRecord);
			// Now, walk backward, removing from the pinned set - note that the first "previous()" call will return nextBackwardSyncRecord.
			while (iterator.hasPrevious())
			{
				pinnedRecords.remove(iterator.previous());
			}
		}
		
		// Process the removed set, adding them to the meta-data to unpin collection and adding any cached leaves to the files to unpin collection.
		for (IpfsFile removedRecord : removedRecords)
		{
			if (pinnedRecords.contains(removedRecord))
			{
				AbstractRecord record = support.loadCached(removedRecord, AbstractRecord.DESERIALIZER).get();
				FollowingCacheElement cachedObject = support.getCacheDataForElement(removedRecord);
				IpfsFile imageHash = (null != cachedObject) ? cachedObject.imageHash() : null;
				IpfsFile leafHash = (null != cachedObject) ? cachedObject.leafHash() : null;
				IpfsFile audioHash = null;
				IpfsFile videoHash = null;
				int videoEdgeSize = 0;
				if (null != leafHash)
				{
					// We need to find out if the leaf is audio or video, so we can correctly report it.
					for (Leaf leaf : record.getVideoExtension())
					{
						if (leafHash.equals(leaf.cid()))
						{
							if (leaf.mime().startsWith("video/"))
							{
								videoHash = leafHash;
								videoEdgeSize = Math.max(leaf.width(), leaf.height());
							}
							else
							{
								Assert.assertTrue(leaf.mime().startsWith("audio/"));
								audioHash = leafHash;
							}
							break;
						}
					}
					// We must have matched something.
					Assert.assertTrue((null != videoHash) || (null != audioHash));
				}
				support.deferredRemoveMetaDataFromFollowCache(removedRecord);
				if (null != imageHash)
				{
					support.deferredRemoveFileFromFollowCache(imageHash);
				}
				if (null != leafHash)
				{
					support.deferredRemoveFileFromFollowCache(leafHash);
				}
				support.removeElementFromCache(removedRecord, record, imageHash, audioHash, videoHash, videoEdgeSize);
			}
			support.removeRecordForFollowee(removedRecord);
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
		support.logMessageImportant("Standard synchronization processing " + newRecordsBeingProcessedInitial.size() + " new records (" + removedRecords.size() + " have been removed).");
		
		// Complete the rest of the work related to this.
		// NOTE:  We always want to add the newest element whether this is a new followee or a refreshed one, so handle that as a special case.
		_finishStartedRecordSync(support, prefs, null, null, newRecordsBeingProcessedInitial, currentCacheUsageInBytes, true, true);
		
		// It is possible that we are synchronizing forward despite there still being backward synchronization information so make sure we didn't break it.
		if (null != nextBackwardSyncRecord)
		{
			// Check that this is still in the new list.
			Set<IpfsFile> newRecordSet = new HashSet<>(newRecords.getRecordList());
			boolean isStillPresent = newRecordSet.contains(nextBackwardSyncRecord);
			if (!isStillPresent)
			{
				// The record is missing so walk backward in the old list and check each of those until we find a match, then we can use that as the next backward sync record.
				ListIterator<IpfsFile> iterator = _prepareBackwardIterator(oldRecords, nextBackwardSyncRecord);
				// Now, walk backward until we find a match - note that the first "previous()" call will return nextBackwardSyncRecord.
				while ((null != nextBackwardSyncRecord) && !newRecordSet.contains(nextBackwardSyncRecord))
				{
					nextBackwardSyncRecord = iterator.hasPrevious()
							? iterator.previous()
							: null
					;
				}
				// It is possible that we walked off the list but we can write this back, either way.
				support.setNextBackwardRecord(nextBackwardSyncRecord);
			}
		}
	}

	// NOTE:  We have no way of safely failing in the backward sync so we need to handle the exceptions by recording skipped elements.
	private static void _refreshRecordsBackward(IRefreshSupport support
			, PrefsData prefs
			, IpfsFile newRecordsElement
			, IpfsFile nextBackwardSyncRecord
			, long currentCacheUsageInBytes
			, boolean isFirstSync
	)
	{
		AbstractRecords newRecords;
		try
		{
			newRecords = _loadRecords(support, newRecordsElement);
		}
		catch (FailedDeserializationException e)
		{
			// This can't happen since we already pinned this.
			throw Assert.unexpected(e);
		}
		catch (IpfsConnectionException e)
		{
			// This means a problem with the node, so just do nothing and we will retry, later.
			support.logMessageImportant("Failed to load records for backward sync: " + e.getLocalizedMessage());
			newRecords = null;
		}
		
		if (null != newRecords)
		{
			// We want to notify that we have seen everything, if this is the first call.
			if (isFirstSync)
			{
				List<IpfsFile> recordList = newRecords.getRecordList();
				for (IpfsFile cid : recordList)
				{
					support.addRecordForFollowee(cid);
				}
				support.logMessageImportant("First synchronization reported " + recordList.size() + " new records (will be incrementally pinned).");
			}
			ListIterator<IpfsFile> iterator = _prepareBackwardIterator(newRecords, nextBackwardSyncRecord);
			// Start walking backward - the first previous() call will return nextBackwardSyncRecord, which is our first candidate.
			int remaining = INCREMENTAL_RECORD_COUNT;
			List<RawElementData> newRecordsBeingProcessedInitial = new ArrayList<>();
			while ((remaining > 0) && iterator.hasPrevious())
			{
				IpfsFile recordToSync = iterator.previous();
				remaining -= 1;
				
				// Start the initial sync for this record.
				RawElementData data = new RawElementData();
				data.elementCid = recordToSync;
				data.futureSize = support.getSizeInBytes(recordToSync);
				newRecordsBeingProcessedInitial.add(data);
			}
			
			// Complete the rest of the work related to this.
			List<IpfsFile> permanentFailures = new ArrayList<>();
			List<IpfsFile> temporaryFailures = new ArrayList<>();
			try
			{
				// Note that we built this list by walking backward, so it is newest-first, but the core algorithm assumes oldest-first.
				Collections.reverse(newRecordsBeingProcessedInitial);
				_finishStartedRecordSync(support, prefs, permanentFailures, temporaryFailures, newRecordsBeingProcessedInitial, currentCacheUsageInBytes, isFirstSync, false);
			}
			catch (SizeConstraintException | FailedDeserializationException | IpfsConnectionException e)
			{
				// Exceptions are only thrown in the case where we DON'T pass in the failure lists.
				throw Assert.unexpected(e);
			}
			
			IpfsFile updatedNextRecord = iterator.hasPrevious()
					? iterator.previous()
					: null
			;
			support.setNextBackwardRecord(updatedNextRecord);
			for (IpfsFile failure : permanentFailures)
			{
				support.addSkippedRecord(failure, true);
			}
			for (IpfsFile failure : temporaryFailures)
			{
				support.addSkippedRecord(failure, false);
			}
			support.logMessageImportant("Incremental sync completed"
					+ ((null != updatedNextRecord) ? " (more to do)" : " (done)")
					+ ", processing " + newRecordsBeingProcessedInitial.size() + " records"
					+ (permanentFailures.isEmpty() ? "" : " (added " + permanentFailures.size() + " permanent failures)")
					+ (temporaryFailures.isEmpty() ? "" : " (added " + temporaryFailures.size() + " temporary failures)")
			);
		}
	}

	private static void _finishStartedRecordSync(IRefreshSupport support
			, PrefsData prefs
			, List<IpfsFile> out_permanentFailures
			, List<IpfsFile> out_temporaryFailures
			, List<RawElementData> oldestFirstNewRecordsBeingProcessedInitial
			, long currentCacheUsageInBytes
			, boolean forceSelectFirstElement
			, boolean shouldReportRecordFound
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
	{
		// We want to verify that we handle all of the elements in oldestFirstRecordCandidates, somehow (either cache or skip).
		int initialCandidateListSize = oldestFirstNewRecordsBeingProcessedInitial.size();
		int elementsCached = 0;
		
		// Now, wait for all the sizes to come back and only pin elements which are below our size threshold.
		List<RawElementData> newRecordsBeingProcessedSizeChecked = new ArrayList<>();
		support.logMessageVerbose("Checking sizes of new records (checking " + oldestFirstNewRecordsBeingProcessedInitial.size() + " records)...");
		for (RawElementData data : oldestFirstNewRecordsBeingProcessedInitial)
		{
			// A connection exception here will cause refresh to fail.
			try
			{
				data.size = data.futureSize.get();
				// If this element is too big, we won't pin it or consider caching it (this is NOT refresh failure).
				// Note that this means any path which directly reads this element should check the size to see if it is present.
				if (data.size <= AbstractRecord.SIZE_LIMIT_BYTES)
				{
					data.futureElementPin = support.addMetaDataToFollowCache(data.elementCid);
					newRecordsBeingProcessedSizeChecked.add(data);
				}
				else if (null != out_permanentFailures)
				{
					out_permanentFailures.add(data.elementCid);
				}
				else
				{
					throw new SizeConstraintException("record", data.size, AbstractRecord.SIZE_LIMIT_BYTES);
				}
			}
			catch (IpfsConnectionException e)
			{
				if (null != out_temporaryFailures)
				{
					out_temporaryFailures.add(data.elementCid);
				}
				else
				{
					throw e;
				}
			}
		}
		oldestFirstNewRecordsBeingProcessedInitial = null;
		
		// Now, wait for all the pins of the elements and check the sizes of their leaves.
		List<RawElementData> newRecordsBeingProcessedCalculatingLeaves = new ArrayList<>();
		support.logMessageVerbose("Waiting for meta-data to be pinned (pinning " + newRecordsBeingProcessedSizeChecked.size() + " records)...");
		for (RawElementData data : newRecordsBeingProcessedSizeChecked)
		{
			// A connection exception here will cause refresh to fail.
			try
			{
				data.futureElementPin.get();
				data.futureElementPin = null;
				// We pinned this so the read should be pretty-well instantaneous.
				try
				{
					data.record = support.loadCached(data.elementCid, AbstractRecord.DESERIALIZER).get();
				}
				catch (FailedDeserializationException e)
				{
					if (null != out_permanentFailures)
					{
						support.deferredRemoveMetaDataFromFollowCache(data.elementCid);
						out_permanentFailures.add(data.elementCid);
					}
					else
					{
						throw e;
					}
				}
				if (null != data.record)
				{
					// We will decide on what leaves to pin, but we will still decide to cache this even if there aren't any leaves.
					_selectLeavesForElement(support, data, data.record, prefs.videoEdgePixelMax);
					newRecordsBeingProcessedCalculatingLeaves.add(data);
				}
			}
			catch (IpfsConnectionException e)
			{
				if (null != out_temporaryFailures)
				{
					out_temporaryFailures.add(data.elementCid);
				}
				else
				{
					throw e;
				}
			}
		}
		newRecordsBeingProcessedSizeChecked = null;
		
		// Now, we wait for the sizes to come back and then choose which elements to cache.
		List<CacheAlgorithm.Candidate<RawElementData>> candidates = new ArrayList<>();
		support.logMessageVerbose("Checking sizes of attachments (checking for " + newRecordsBeingProcessedCalculatingLeaves.size() + " records)...");
		for (RawElementData data : newRecordsBeingProcessedCalculatingLeaves)
		{
			boolean bothLoaded = true;
			// If we fail to fetch the sizes of any of the leaves, we will just decide to proceed without them.
			if (null != data.thumbnailSizeFuture)
			{
				try
				{
					data.thumbnailSizeBytes = data.thumbnailSizeFuture.get();
					// Make sure that this isn't over our preference limit.
					if (data.thumbnailSizeBytes > prefs.followeeRecordThumbnailMaxBytes)
					{
						bothLoaded = false;
						support.logMessageImportant("Attachments for record " + data.elementCid + " are being skipped since its thumbnail is " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " which is above the prefs limit of " + MiscHelpers.humanReadableBytes(prefs.followeeRecordThumbnailMaxBytes));
					}
				}
				catch (IpfsConnectionException e)
				{
					support.logMessageImportant("Failed to load size for thumbnail for " + data.elementCid + ": " + data.thumbnailHash);
					bothLoaded = false;
				}
				data.thumbnailSizeFuture = null;
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
						support.logMessageImportant("Attachments for record " + data.elementCid + " is being skipped since its leaf is " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " which is above the prefs limit of " + MiscHelpers.humanReadableBytes(relevantSizeBytes));
					}
				}
				catch (IpfsConnectionException e)
				{
					bothLoaded = false;
					support.logMessageImportant("Failed to load size for leaf for " + data.elementCid + ": " + data.leafHash);
				}
				data.leafSizeFuture = null;
			}
			// We will only try to pin leaves if we could fetch the sizes for all of them.
			long byteSize = 0L;
			if (bothLoaded)
			{
				byteSize += data.thumbnailSizeBytes;
				byteSize += data.leafSizeBytes;
			}
			else
			{
				// We failed to load at least one of the sizes so we will abandon our attempt to pin any leaves.
				data.thumbnailHash = null;
				data.leafHash = null;
				data.audioLeafHash = null;
				data.videoLeafHash = null;
			}
			CacheAlgorithm.Candidate<RawElementData> candidate = new CacheAlgorithm.Candidate<RawElementData>(byteSize, data);
			candidates.add(candidate);
		}
		newRecordsBeingProcessedCalculatingLeaves = null;
		
		List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = _selectCandidatesForAddition(prefs, currentCacheUsageInBytes, forceSelectFirstElement, candidates);
		// Note that we still need to produce a cached record decision for this, even if we don't want to cache the leaves.
		if (finalSelection.size() != candidates.size())
		{
			// We _could_ use identity for this but relying on that seems dangerous.
			Set<IpfsFile> selectedElementSet = finalSelection.stream()
					.map((CacheAlgorithm.Candidate<RawElementData> candidate) -> candidate.data().elementCid)
					.collect(Collectors.toSet())
			;
			for (CacheAlgorithm.Candidate<RawElementData> candidate : candidates)
			{
				RawElementData data = candidate.data();
				if (!selectedElementSet.contains(data.elementCid))
				{
					// This was passed over so just create the degenerate cache result.
					Assert.assertTrue(null != data.record);
					support.cacheRecordForFollowee(data.elementCid, data.record, null, null, null, 0, 0L);
					elementsCached += 1;
				}
			}
		}
		candidates = null;
		
		// We can now walk the final selection and pin all the relevant elements.
		List<RawElementData> candidatesBeingPinned = new ArrayList<>();
		support.logMessageVerbose("Pinning all attachments (selected " + finalSelection.size() + " records)...");
		for (CacheAlgorithm.Candidate<RawElementData> candidate : finalSelection)
		{
			RawElementData data = candidate.data();
			support.logMessageVerbose("Pinning attachments for record " + data.elementCid + "...");
			if (null != data.thumbnailHash)
			{
				support.logMessageVerbose("\t-thumbnail " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " (" + data.thumbnailHash + ")...");
				data.futureThumbnailPin = support.addFileToFollowCache(data.thumbnailHash);
			}
			if (null != data.leafHash)
			{
				support.logMessageVerbose("\t-leaf " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " (" + data.leafHash + ")...");
				data.futureLeafPin = support.addFileToFollowCache(data.leafHash);
			}
			candidatesBeingPinned.add(data);
		}
		finalSelection = null;
		
		// Finally, walk the records whose leaves we pinned and build FollowingCacheElement instances for each.
		support.logMessageVerbose("Waiting for all attachments to be pinned (" + candidatesBeingPinned.size() + " records)...");
		for (RawElementData data : candidatesBeingPinned)
		{
			support.logMessageVerbose("Waiting for attachments for record " + data.elementCid + "...");
			boolean allLeavesSuccess = true;
			if (null != data.futureThumbnailPin)
			{
				try
				{
					data.futureThumbnailPin.get();
				}
				catch (IpfsConnectionException e)
				{
					// We failed the pin so drop this element.
					support.logMessageImportant("Failed to pin thumbnail for " + data.elementCid + ": " + data.thumbnailHash);
					allLeavesSuccess = false;
					data.thumbnailHash = null;
					data.thumbnailSizeBytes = 0;
				}
			}
			if (null != data.futureLeafPin)
			{
				try
				{
					data.futureLeafPin.get();
				}
				catch (IpfsConnectionException e)
				{
					// We failed the pin so drop this element.
					support.logMessageImportant("Failed to pin leaf for " + data.elementCid + ": " + data.leafHash);
					allLeavesSuccess = false;
					data.leafHash = null;
					data.audioLeafHash = null;
					data.videoLeafHash = null;
					data.videoEdgeSize = 0;
					data.leafSizeBytes = 0;
				}
			}
			
			// Whether or not we pinned any leaves, record that we saw this (in this non-incremental path, we do this after pinning the meta-data).
			if (shouldReportRecordFound)
			{
				support.addRecordForFollowee(data.elementCid);
			}
			support.logMessageVerbose("Successfully pinned attachments for record " + data.elementCid + "!");
			
			// We will only proceed to add leaves to the cache if everything was pinned and there were leaf elements.
			// (Note that we don't record elements without leaves since we always cache meta-data, anyway)
			if (allLeavesSuccess)
			{
				if (null != data.thumbnailHash)
				{
					support.logMessageVerbose("\t-thumnail " + MiscHelpers.humanReadableBytes(data.thumbnailSizeBytes) + " (" + data.thumbnailHash + ")");
				}
				if (null != data.leafHash)
				{
					support.logMessageVerbose("\t-leaf " + MiscHelpers.humanReadableBytes(data.leafSizeBytes) + " (" + data.leafHash + ")");
				}
			}
			else
			{
				// We may have only partially failed so see which we may need to unpin.
				if (null != data.thumbnailHash)
				{
					support.deferredRemoveFileFromFollowCache(data.thumbnailHash);
					data.thumbnailHash = null;
					data.thumbnailSizeBytes = 0;
				}
				if (null != data.leafHash)
				{
					support.deferredRemoveFileFromFollowCache(data.leafHash);
					data.leafHash = null;
					data.audioLeafHash = null;
					data.videoLeafHash = null;
					data.videoEdgeSize = 0;
					data.leafSizeBytes = 0;
				}
			}
			// Notify the support that we pinned the record and leaves leaves.
			support.cacheRecordForFollowee(data.elementCid, data.record, data.thumbnailHash, data.audioLeafHash, data.videoLeafHash, data.videoEdgeSize, data.thumbnailSizeBytes + data.leafSizeBytes);
			elementsCached += 1;
		}
		// Make sure that we didn't miss anything.
		int permanentSkips = (null != out_permanentFailures)
				? out_permanentFailures.size()
				: 0
		;
		int temporarySkips = (null != out_temporaryFailures)
				? out_temporaryFailures.size()
				: 0
		;
		Assert.assertTrue(initialCandidateListSize == (permanentSkips + temporarySkips + elementsCached));
	}

	private static List<CacheAlgorithm.Candidate<RawElementData>> _selectCandidatesForAddition(PrefsData prefs
			, long currentCacheUsageInBytes
			, boolean forceSelectFirstElement
			, List<CacheAlgorithm.Candidate<RawElementData>> oldestFirstCandidates
	)
	{
		// NOTE:  The list we are given and the list we return should be in the order of how they are specified in the
		// user's meta-data entry, but those are oldest-first.  Since our cache algorithm favours earlier elements, and
		// we want those to be the newest elements, we need to reverse the list of candidates and then reverse the list
		// selected.
		List<CacheAlgorithm.Candidate<RawElementData>> newestFirstCandidates = new ArrayList<>(oldestFirstCandidates);
		Collections.reverse(newestFirstCandidates);
		
		// Also remember that we need to add this size to the cache since it counts as already being selected.
		// TODO:  Refactor this special logic into some kind of pluggable "cache strategy" for more reliable testing and more exotic performance considerations.
		long effectiveCacheUsedBytes = currentCacheUsageInBytes;
		List<CacheAlgorithm.Candidate<RawElementData>> finalSelection = new ArrayList<>();
		if (forceSelectFirstElement && (newestFirstCandidates.size() > 0))
		{
			CacheAlgorithm.Candidate<RawElementData> firstElement = newestFirstCandidates.remove(0);
			effectiveCacheUsedBytes += firstElement.byteSize();
			finalSelection.add(firstElement);
		}
		CacheAlgorithm algorithm = new CacheAlgorithm(prefs.followeeCacheTargetBytes, effectiveCacheUsedBytes);
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

	private static void _checkSizeInline(IRefreshSupport support, String context, IpfsFile element, long sizeLimit) throws IpfsConnectionException, SizeConstraintException
	{
		long size = support.getSizeInBytes(element).get();
		if (size > sizeLimit)
		{
			throw new SizeConstraintException(context, size, sizeLimit);
		}
	}

	/**
	 * NOTE:  The returned iterator will return nextBackwardSyncRecord on next previous() call.
	 */
	private static ListIterator<IpfsFile> _prepareBackwardIterator(AbstractRecords records, IpfsFile nextBackwardSyncRecord)
	{
		ListIterator<IpfsFile> iterator = records.getRecordList().listIterator();
		// Find the old element and then walk backward (this can't fail since we already selected from this list, before).
		while (!iterator.next().equals(nextBackwardSyncRecord))
		{
			// Just advance.
		}
		return iterator;
	}


	/**
	 * The interface required for external callers to this class.
	 * This just exists to make the external requirements clearer and to make tests simpler.
	 */
	public interface IRefreshSupport
	{
		/**
		 * Logs a message which is considered important (should be used sparingly).
		 * 
		 * @param message The message to log.
		 */
		void logMessageImportant(String message);
		/**
		 * Logs a verbose message (not shown in default modes).
		 * 
		 * @param message The message to log.
		 */
		void logMessageVerbose(String message);
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
		 * @param description The new description.
		 */
		void followeeDescriptionNewOrUpdated(AbstractDescription description);
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
		 * Returns the object describing the data cached for this AbstractRecord element.
		 * 
		 * @param elementHash The CID of the element meta-data XML.
		 * @return The cached data or null, if one wasn't cached.
		 */
		FollowingCacheElement getCacheDataForElement(IpfsFile elementHash);
		/**
		 * Notifies that an element has been found in the record list for a user.
		 * NOTE:  This may or may not be pinned, due to how incremental synchronization works.
		 * 
		 * @param elementHash The CID of the AbstractRecord meta-data XML CID.
		 */
		void addRecordForFollowee(IpfsFile elementHash);
		/**
		 * Notifies that an encountered record has had a caching decision completed.  This means that minimally the
		 * elementHash will be pinned and optionally the imageHash, audioLeaf, or videoLeaf have been cached.
		 * This always happens AFTER addRecordForFollowee() has been called for this element, at some point (might have
		 * been in a previous run, if this is incremental synchronization).
		 * 
		 * @param elementHash The now-pinned meta-data XML CID.
		 * @param recordData The high-level record data.
		 * @param imageHash The now-pinned image data (or null).
		 * @param audioLeaf The now-pinned audio data (or null).
		 * @param videoLeaf The now-pinned video data (or null).
		 * @param videoEdgeSize The edge size of the video (0 if null).
		 * @param combinedLeafSizeBytes The combined size of both the image and video, in bytes.
		 */
		void cacheRecordForFollowee(IpfsFile elementHash
				, AbstractRecord recordData
				, IpfsFile imageHash
				, IpfsFile audioLeaf
				, IpfsFile videoLeaf
				, int videoEdgeSize
				, long combinedLeafSizeBytes
		);
		/**
		 * Notifies that an element has been removed from the record list for a user.
		 * NOTE:  This may or may not be pinned, due to how incremental synchronization works.
		 * 
		 * @param elementHash The CID of the AbstractRecord meta-data XML CID.
		 */
		void removeRecordForFollowee(IpfsFile elementHash);
		/**
		 * Called when a the meta-data of a previously-observed element has been enqueued for unpin and should be
		 * dropped.
		 * Note that this is called after any associated leaves have also been enqueued for unpin.
		 * 
		 * @param elementHash The now-unpinned CID of the meta-data XML.
		 * @param recordData The high-level record data.
		 * @param imageHash The now-unpinned image data (or null).
		 * @param audioHash The now-unpinned audio data (or null).
		 * @param videoHash The now-unpinned video data (or null).
		 * @param videoEdgeSize The edge size of the video (0 if video null).
		 */
		void removeElementFromCache(IpfsFile elementHash
				, AbstractRecord recordData
				, IpfsFile imageHash
				, IpfsFile audioHash
				, IpfsFile videoHash
				, int videoEdgeSize
		);
		/**
		 * @return The next backward record of the followee, for incremental sync (usually null).
		 */
		IpfsFile getNextBackwardRecord();
		/**
		 * Updates the next backward record for the followee (setting null will conclude incremental synchronization).
		 * @param nextBackwardSyncRecord The new record CID for the next incremental sync start (can be null).
		 */
		void setNextBackwardRecord(IpfsFile nextBackwardSyncRecord);
		/**
		 * Records that a given record failed to be loaded during incremental synchronization.
		 * 
		 * @param recordCid The CID of the AbstractRecord.
		 * @param isPermanent True if this is a permanent failure or false if it could be retried in the future.
		 */
		void addSkippedRecord(IpfsFile recordCid, boolean isPermanent);
	}
}
