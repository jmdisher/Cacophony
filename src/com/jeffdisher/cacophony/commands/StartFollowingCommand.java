package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheAlgorithm;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StringHelpers;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		HighLevelCache cache = new HighLevelCache(pinCache, connection);
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		FollowIndex followIndex = local.loadFollowIndex();
		
		// We need to first verify that we aren't already following them.
		IpfsFile lastRoot = followIndex.getLastFetchedRoot(_publicKey);
		if (null != lastRoot)
		{
			throw new UsageException("Already following public key: " + _publicKey.toPublicKey());
		}
		
		// Then, do the initial resolve of the key to make sure the network thinks it is valid.
		IpfsFile indexRoot = scheduler.resolvePublicKey(_publicKey).get();
		Assert.assertTrue(null != indexRoot);
		environment.logToConsole("Resolved as " + indexRoot);
		
		// Verify that this isn't too big.
		long indexSize = scheduler.getSizeInBytes(indexRoot).get();
		if (indexSize > SizeLimits.MAX_INDEX_SIZE_BYTES)
		{
			throw new SizeConstraintException("index", indexSize, SizeLimits.MAX_INDEX_SIZE_BYTES);
		}
		
		// Now, cache the root meta-data structures.
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, indexRoot);
		StreamIndex streamIndex = checker.loadCached(indexRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash);
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash);
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash);
		StreamDescription description = checker.loadCached(descriptionHash, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash);
		
		// Create the initial following state.
		followIndex.addFollowingWithInitialState(_publicKey, indexRoot, System.currentTimeMillis());
		
		// Populate the initial cache records.
		GlobalPrefs prefs = local.readSharedPrefs();
		int videoEdgePixelMax = prefs.videoEdgePixelMax();
		_populateCachedRecords(environment, prefs, scheduler, cache, followIndex, indexRoot, checker.loadCached(recordsHash, (byte[] data) -> GlobalData.deserializeRecords(data)).get(), videoEdgePixelMax);
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		local.writeBackConfig();
		log.finish("Follow successful!");
	}


	private void _populateCachedRecords(IEnvironment environment, GlobalPrefs prefs, INetworkScheduler scheduler, HighLevelCache cache, FollowIndex followIndex, IpfsFile fetchedRoot, StreamRecords newRecords, int videoEdgePixelMax) throws IpfsConnectionException, SizeConstraintException
	{
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		IOperationLog log = environment.logOperation("Caching initial entries...");
		int entryCountAdded = 0;
		int entryCountTotal = newRecords.getRecord().size();
		
		// Queue up the async size checks and make sure that they are ok before we proceed.
		List<FutureSize> futureSizes = new ArrayList<>();
		for (String rawCid : newRecords.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			// Verify that this isn't too big.
			futureSizes.add(scheduler.getSizeInBytes(cid));
		}
		for (FutureSize size : futureSizes)
		{
			long elementSize = size.get();
			// Verify that this isn't too big.
			if (elementSize > SizeLimits.MAX_RECORD_SIZE_BYTES)
			{
				throw new SizeConstraintException("record", elementSize, SizeLimits.MAX_RECORD_SIZE_BYTES);
			}
		}
		
		// Fetch the StreamRecord instances so we can check the sizes of the elements inside.
		List<AsyncRecord> asyncRecordList = new ArrayList<>();
		for (String rawCid : newRecords.getRecord())
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(rawCid);
			
			// Note that we need to add the element meta-data independently of caching the leaves within (since they can be pruned but the meta-data can't).
			cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, cid);
			FutureRead<StreamRecord> future = scheduler.readData(cid, (byte[] data) -> GlobalData.deserializeRecord(data));
			asyncRecordList.add(new AsyncRecord(rawCid, future));
		}
		
		// Now, cache all the element meta-data entries and find their sizes for consideration into the cache.
		List<CacheAlgorithm.Candidate<AsyncRecord>> candidatesList = new ArrayList<>();
		for (AsyncRecord async : asyncRecordList)
		{
			long bytesForLeaves = CacheHelpers.sizeInBytesToAdd(scheduler, videoEdgePixelMax, async.future.get());
			// Note that the candidates are considered with weight on the earlier elements in this list so we want to make sure the more recent ones appear there.
			candidatesList.add(0, new CacheAlgorithm.Candidate<AsyncRecord>(bytesForLeaves, async));
		}
		
		if (!candidatesList.isEmpty())
		{
			long currentTimeMillis = System.currentTimeMillis();
			CacheAlgorithm.Candidate<AsyncRecord> firstElement = candidatesList.remove(0);
			
			// Make sure that we will have enough space to cache this new channel (we pro-actively clear some of the cache to make some space when adding a new followee).
			// We want to make sure that this will minimally hold the most recent entry.
			long maxCacheFullBytes = prefs.followCacheTargetBytes() - firstElement.byteSize();
			long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
			long reducedCacheSizeBytes = Math.min(maxCacheFullBytes, CacheHelpers.getTargetCacheSizeBeforeNewChannel(prefs));
			CacheAlgorithm pruningAlgorithm = new CacheAlgorithm(reducedCacheSizeBytes, currentCacheSizeBytes);
			CacheHelpers.pruneCacheIfNeeded(cache, followIndex, pruningAlgorithm, _publicKey, 0L);
			
			// We always want to cache the most recent, since it is likely most watched, so remove that from the list and add it before running the general algorithm.
			environment.logToConsole("Caching most recent entry: " + firstElement.data());
			entryCountAdded += 1;
			long leafBytes = CacheHelpers.addElementToCache(scheduler, cache, followIndex, _publicKey, fetchedRoot, videoEdgePixelMax, currentTimeMillis, firstElement.data().rawCid, firstElement.data().future.get());
			environment.logToConsole("\tleaf elements: " + StringHelpers.humanReadableBytes(leafBytes));
			
			// Finally, run the cache algorithm for bulk adding and then cache whatever we are told to add.
			CacheAlgorithm algorithm = new CacheAlgorithm(prefs.followCacheTargetBytes(), CacheHelpers.getCurrentCacheSizeBytes(followIndex));
			List<CacheAlgorithm.Candidate<AsyncRecord>> toAdd = algorithm.toAddInNewAddition(candidatesList);
			for (CacheAlgorithm.Candidate<AsyncRecord> elt : toAdd)
			{
				environment.logToConsole("Caching entry: " + elt.data());
				entryCountAdded += 1;
				leafBytes = CacheHelpers.addElementToCache(scheduler, cache, followIndex, _publicKey, fetchedRoot, videoEdgePixelMax, currentTimeMillis, elt.data().rawCid, elt.data().future.get());
				environment.logToConsole("\tleaf elements: " + StringHelpers.humanReadableBytes(leafBytes));
			}
		}
		log.finish("Completed initial cache (" + entryCountAdded + " of " + entryCountTotal + " entries cached)");
	}


	private static record AsyncRecord(String rawCid, FutureRead<StreamRecord> future)
	{
	}
}
