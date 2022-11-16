package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StringHelpers;


/**
 * This class contains miscellaneous common logic required by many of the commands.  There is no strong design to it
 * other than being entirely static.
 */
public class CommandHelpers
{
	/**
	 * The common idiom of serializing, saving to IPFS, and republishing the index to IPNS.
	 * Failures in saving will throw an exception while a failure in publication is merely logged (as this is
	 * considered a degradation, but not a failure).
	 * 
	 * @param remote The remote helpers.
	 * @param streamIndex The index to use as the updated root of the data structure.
	 * @return The asynchronous publish operation.
	 * @throws IpfsConnectionException An error occurred while saving the file to IPFS.
	 */
	public static FuturePublish serializeSaveAndPublishIndex(IEnvironment environment, INetworkScheduler scheduler, StreamIndex streamIndex) throws IpfsConnectionException
	{
		// Serialize the index file and save it to the IPFS node.
		IpfsFile hashIndex = scheduler.saveStream(new ByteArrayInputStream(GlobalData.serializeIndex(streamIndex)), true).get();
		// This never returns null.
		Assert.assertTrue(null != hashIndex);
		// Publish it to IPNS (returns error on failure).
		return scheduler.publishIndex(hashIndex);
	}

	/**
	 * Waits for the publish operation in-flight in asyncPublish to complete or fail, logging the result.
	 * 
	 * @param environment Used for logging.
	 * @param asyncPublish The in-flight asynchronous publication.
	 */
	public static void commonWaitForPublish(IEnvironment environment, FuturePublish asyncPublish)
	{
		StandardEnvironment.IOperationLog log = environment.logOperation("Waiting for publish " + asyncPublish.getIndexHash());
		IpfsConnectionException error = asyncPublish.get();
		if (null == error)
		{
			log.finish("Success!");
		}
		else
		{
			log.finish("Failed: " + error.getLocalizedMessage());
			environment.logError("WARNING:  Failed to publish new entry to IPNS (the post succeeded, but a republish will be required): " + asyncPublish.getIndexHash());
		}
	}

	public static void queueAndProcessElementRecordSize(INetworkScheduler scheduler, List<RawElementData> workingRecordList) throws IpfsConnectionException, SizeConstraintException
	{
		_queueAndProcessElementRecordSize(scheduler, workingRecordList);
	}

	public static List<CacheAlgorithm.Candidate<RawElementData>> fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException
	{
		return _fetchLeafSizes(scheduler, videoEdgePixelMax, workingRecordList);
	}

	/**
	 * Walks the local data to determine the current cache size.  If the size is greater than the target size in the
	 * prefs, it will unpin elements until it fits.
	 * 
	 * @param environment The execution environment.
	 * @param local The local storage abstraction.
	 * @param fullnessFraction The fraction of the target we should consider our target (1.0 means full cache).
	 * @throws IpfsConnectionException If something goes wrong interacting with the IPFS node.
	 */
	public static void shrinkCacheToFitInPrefs(IEnvironment environment, LocalConfig local, double fullnessFraction) throws IpfsConnectionException
	{
		IOperationLog log = environment.logOperation("Checking is cache requires shrinking...");
		IReadWriteLocalData data = local.getSharedLocalData().openForWrite();
		FollowIndex followIndex = data.readFollowIndex();
		LocalIndex localIndex = data.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = data.readGlobalPinCache();
		GlobalPrefs globalPrefs = data.readGlobalPrefs();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		
		try
		{
			long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
			long targetSizeBytes = (long)(globalPrefs.followCacheTargetBytes() * fullnessFraction);
			if (currentCacheSizeBytes > targetSizeBytes)
			{
				environment.logToConsole("Pruning cache to " + StringHelpers.humanReadableBytes(targetSizeBytes) + " from current size of " + StringHelpers.humanReadableBytes(currentCacheSizeBytes) + "...");
				long bytesToAdd = 0L;
				CacheHelpers.pruneCacheIfNeeded(cache, followIndex, new CacheAlgorithm(targetSizeBytes, currentCacheSizeBytes), bytesToAdd);
			}
			else
			{
				environment.logToConsole("Not pruning cache since " + StringHelpers.humanReadableBytes(currentCacheSizeBytes) + " is below target of " + StringHelpers.humanReadableBytes(targetSizeBytes));
			}
		}
		finally
		{
			// TODO:  Make sure that nothing else in the state is left broken.
			data.writeFollowIndex(followIndex);
			data.writeGlobalPinCache(pinCache);
			data.close();
		}
		log.finish("Cache clean finished without issue");
	}

	public static FollowRecord doRefreshOfRecord(IEnvironment environment
			, INetworkScheduler scheduler
			, HighLevelCache cache
			, LoadChecker checker
			, long currentCacheUsageInBytes
			, IpfsKey publicKey
			, FollowRecord startRecord
			, IpfsFile indexRoot
			, GlobalPrefs prefs
	) throws IpfsConnectionException, SizeConstraintException
	{
		// Handle the differences between start and refresh.
		FollowingCacheElement[] startElements = (null != startRecord)
				? startRecord.elements()
				: new FollowingCacheElement[0]
		;
		IpfsFile lastFetchedRoot = (null != startRecord)
				? startRecord.lastFetchedRoot()
				: null
		;
		
		// Prepare for the initial fetch.
		StandardRefreshSupport refreshSupport = new StandardRefreshSupport(environment, scheduler, cache, checker);
		IpfsFile successfulIndex = null;
		FollowingCacheElement[] elementsToWrite = null;
		try
		{
			elementsToWrite = FolloweeRefreshLogic.refreshFollowee(refreshSupport
					, prefs
					, startElements
					, lastFetchedRoot
					, indexRoot
					, currentCacheUsageInBytes
			);
			successfulIndex = indexRoot;
		}
		catch (IpfsConnectionException e)
		{
			// If we were looking at the first attempt, just throw the exception.
			if (null == lastFetchedRoot)
			{
				throw e;
			}
			environment.logToConsole("Network failure in refresh: " + e.getLocalizedMessage());
			environment.logToConsole("Refresh aborted and will be retried in the future");
			elementsToWrite = startElements;
			successfulIndex = lastFetchedRoot;
		}
		catch (SizeConstraintException e)
		{
			// If we were looking at the first attempt, just throw the exception.
			if (null == lastFetchedRoot)
			{
				throw e;
			}
			environment.logToConsole("Root index element too big (probably wrong file published): " + e.getLocalizedMessage());
			environment.logToConsole("Refresh aborted and will be retried in the future");
			elementsToWrite = startElements;
			successfulIndex = lastFetchedRoot;
		}
		
		// Create and save the updated record (if this was an abort, this just has the impact of updating the time).
		Assert.assertTrue(null != successfulIndex);
		Assert.assertTrue(null != elementsToWrite);
		return new FollowRecord(publicKey, successfulIndex, System.currentTimeMillis(), elementsToWrite);
	}

	/**
	 * A common utility to unpin a file from the local IPFS node and remove it from the HighLevelCache.  Since errors
	 * here are rare, and only represent a small leak of local node data, this call just logs them.
	 * 
	 * @param environment Used for logging an error.
	 * @param cache The cache which tracks the state of the local cache and does a write-through to the local IPFS node.
	 * @param file The file to unpin.
	 */
	public static void safeRemoveFromLocalNode(IEnvironment environment, HighLevelCache cache, IpfsFile file)
	{
		_safeRemove(environment, cache, file);
	}

	/**
	 * The common idiom for updating the index after any kind of change:  Updates the local storage and removes the old
	 * index from the cache and local node.
	 * 
	 * @param environment Used for logging.
	 * @param localData A representation of the local data store to modify when writing-back the updated LocalIndex.
	 * @param oldLocalIndex The previous LocalIndex (for extracting unchanged data).
	 * @param cache The local representation of what is cached on this node.
	 * @param oldIndexFile The previous index root file.
	 * @param newIndexHash the new index root file.
	 */
	public static void commonUpdateIndex(IEnvironment environment, IReadWriteLocalData localData, LocalIndex oldLocalIndex, HighLevelCache cache, IpfsFile oldIndexFile, IpfsFile newIndexHash)
	{
		// Update the local index.
		localData.writeLocalIndex(new LocalIndex(oldLocalIndex.ipfsHost(), oldLocalIndex.keyName(), newIndexHash));
		cache.uploadedToThisCache(newIndexHash);
		
		// Remove the old root.
		_safeRemove(environment, cache, oldIndexFile);
	}


	private static void _queueAndProcessElementRecordSize(INetworkScheduler scheduler, List<RawElementData> workingRecordList) throws IpfsConnectionException, SizeConstraintException
	{
		// Queue up the async size checks and make sure that they are ok before we proceed.
		workingRecordList.forEach((RawElementData data) -> {
			data.futureSize = scheduler.getSizeInBytes(data.elementCid);
		});
		for (RawElementData data : workingRecordList)
		{
			long elementSize = data.futureSize.get();
			// Verify that this isn't too big.
			if (elementSize > SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				throw new SizeConstraintException("record", elementSize, SizeLimits.MAX_RECORD_SIZE_BYTES);
			}
			data.size = elementSize;
			data.futureSize = null;
		}
	}

	private static List<CacheAlgorithm.Candidate<RawElementData>> _fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException
	{
		for (RawElementData data : workingRecordList)
		{
			data.record = data.futureRecord.get();
			data.futureRecord = null;
			CacheHelpers.chooseAndFetchLeafSizes(scheduler, videoEdgePixelMax, data);
		}
		// Resolve all sizes;
		List<CacheAlgorithm.Candidate<RawElementData>> candidatesList = new ArrayList<>();
		for (RawElementData data : workingRecordList)
		{
			// We will drop entries if we fail to look them up.
			boolean isVerified = false;
			long bytesForLeaves = 0L;
			try
			{
				// Now, find the size of the relevant leaves within.
				if (null != data.thumbnailSizeFuture)
				{
					data.thumbnailSize = data.thumbnailSizeFuture.get();
					data.thumbnailSizeFuture = null;
					bytesForLeaves += data.thumbnailSize;
				}
				if (null != data.videoSizeFuture)
				{
					data.videoSize = data.videoSizeFuture.get();
					data.videoSizeFuture = null;
					bytesForLeaves += data.videoSize;
				}
				isVerified = true;
			}
			catch (IpfsConnectionException e)
			{
				// We failed to load some of the leaves so we will skip this.
				isVerified = false;
			}
			
			if (isVerified)
			{
				// We didn't hit an exception so we will add it to the set.
				// Note that the candidates are considered with weight on the earlier elements in this list so we want to make sure the more recent ones appear there.
				candidatesList.add(0, new CacheAlgorithm.Candidate<RawElementData>(bytesForLeaves, data));
			}
		}
		return candidatesList;
	}

	private static void _safeRemove(IEnvironment environment, HighLevelCache cache, IpfsFile file)
	{
		try
		{
			cache.removeFromThisCache(file).get();
		}
		catch (IpfsConnectionException e)
		{
			environment.logError("WARNING: Error unpinning " + file + ".  This will need to be done manually.");
		}
	}
}
