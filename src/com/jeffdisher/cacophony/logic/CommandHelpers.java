package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
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

	public static List<CacheAlgorithm.Candidate<RawElementData>> fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException, FailedDeserializationException
	{
		return _fetchLeafSizes(scheduler, videoEdgePixelMax, workingRecordList);
	}

	/**
	 * Walks the local data to determine the current cache size.  If the size is greater than the target size in the
	 * prefs, it will unpin elements until it fits.
	 * 
	 * @param environment The execution environment.
	 * @param access Write access.
	 * @param fullnessFraction The fraction of the target we should consider our target (1.0 means full cache).
	 * @throws IpfsConnectionException If something goes wrong interacting with the IPFS node.
	 */
	public static void shrinkCacheToFitInPrefs(IEnvironment environment, IWritingAccess access, double fullnessFraction) throws IpfsConnectionException
	{
		IOperationLog log = environment.logOperation("Checking is cache requires shrinking...");
		FollowIndex followIndex = access.readWriteFollowIndex();
		GlobalPrefs globalPrefs = access.readGlobalPrefs();
		
		{
			long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
			long targetSizeBytes = (long)(globalPrefs.followCacheTargetBytes() * fullnessFraction);
			if (currentCacheSizeBytes > targetSizeBytes)
			{
				environment.logToConsole("Pruning cache to " + StringHelpers.humanReadableBytes(targetSizeBytes) + " from current size of " + StringHelpers.humanReadableBytes(currentCacheSizeBytes) + "...");
				long bytesToAdd = 0L;
				CacheHelpers.pruneCacheIfNeeded(access, followIndex, new CacheAlgorithm(targetSizeBytes, currentCacheSizeBytes), bytesToAdd);
			}
			else
			{
				environment.logToConsole("Not pruning cache since " + StringHelpers.humanReadableBytes(currentCacheSizeBytes) + " is below target of " + StringHelpers.humanReadableBytes(targetSizeBytes));
			}
		}
		log.finish("Cache clean finished without issue");
	}

	public static FollowRecord doRefreshOfRecord(IEnvironment environment
			, IWritingAccess access
			, long currentCacheUsageInBytes
			, IpfsKey publicKey
			, FollowRecord startRecord
			, IpfsFile indexRoot
			, GlobalPrefs prefs
	) throws IpfsConnectionException, SizeConstraintException, FailedDeserializationException
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
		StandardRefreshSupport refreshSupport = new StandardRefreshSupport(environment, access, startElements);
		IpfsFile successfulIndex = null;
		FollowingCacheElement[] elementsToWrite = null;
		try
		{
			FolloweeRefreshLogic.refreshFollowee(refreshSupport
					, prefs
					, lastFetchedRoot
					, indexRoot
					, currentCacheUsageInBytes
			);
			elementsToWrite = refreshSupport.applyAndReturnElements();
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
		if (null != indexRoot)
		{
			Assert.assertTrue(null != successfulIndex);
		}
		Assert.assertTrue(null != elementsToWrite);
		return new FollowRecord(publicKey, successfulIndex, System.currentTimeMillis(), elementsToWrite);
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

	private static List<CacheAlgorithm.Candidate<RawElementData>> _fetchLeafSizes(INetworkScheduler scheduler, int videoEdgePixelMax, List<RawElementData> workingRecordList) throws IpfsConnectionException, FailedDeserializationException
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
					data.thumbnailSizeBytes = data.thumbnailSizeFuture.get();
					data.thumbnailSizeFuture = null;
					bytesForLeaves += data.thumbnailSizeBytes;
				}
				if (null != data.leafSizeFuture)
				{
					data.leafSizeBytes = data.leafSizeFuture.get();
					data.leafSizeFuture = null;
					bytesForLeaves += data.leafSizeBytes;
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
}
