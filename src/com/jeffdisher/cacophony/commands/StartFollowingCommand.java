package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheAlgorithm;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RawElementData;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


public record StartFollowingCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _publicKey);
		
		IOperationLog log = environment.logOperation("Attempting to follow " + _publicKey + "...");
		LocalConfig local = environment.loadExistingConfig();
		IReadWriteLocalData localData = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = localData.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = localData.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		FollowIndex followIndex = localData.readFollowIndex();
		
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
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, indexRoot).get();
		StreamIndex streamIndex = checker.loadCached(indexRoot, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		Assert.assertTrue(1 == streamIndex.getVersion());
		IpfsFile descriptionHash = IpfsFile.fromIpfsCid(streamIndex.getDescription());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, descriptionHash).get();
		IpfsFile recommendationsHash = IpfsFile.fromIpfsCid(streamIndex.getRecommendations());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recommendationsHash).get();
		IpfsFile recordsHash = IpfsFile.fromIpfsCid(streamIndex.getRecords());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, recordsHash).get();
		StreamDescription description = checker.loadCached(descriptionHash, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		IpfsFile pictureHash = IpfsFile.fromIpfsCid(description.getPicture());
		cache.addToFollowCache(_publicKey, HighLevelCache.Type.METADATA, pictureHash).get();
		
		// Create the initial following state.
		followIndex.addFollowingWithInitialState(_publicKey, indexRoot, System.currentTimeMillis());
		
		// Populate the initial cache records.
		GlobalPrefs prefs = localData.readGlobalPrefs();
		int videoEdgePixelMax = prefs.videoEdgePixelMax();
		_populateCachedRecords(environment, prefs, scheduler, cache, followIndex, indexRoot, checker.loadCached(recordsHash, (byte[] data) -> GlobalData.deserializeRecords(data)).get(), videoEdgePixelMax);
		// TODO: Handle the errors in partial load of a followee so we can still progress and save back, here.
		localData.writeFollowIndex(followIndex);
		localData.writeGlobalPinCache(pinCache);
		localData.writeLocalIndex(localIndex);
		localData.close();
		log.finish("Follow successful!");
	}


	private void _populateCachedRecords(IEnvironment environment, GlobalPrefs prefs, INetworkScheduler scheduler, HighLevelCache cache, FollowIndex followIndex, IpfsFile fetchedRoot, StreamRecords newRecords, int videoEdgePixelMax) throws IpfsConnectionException, SizeConstraintException
	{
		// Note that we always cache the CIDs of the records, whether or not we cache the leaf data files within (since these record elements are tiny).
		IOperationLog log = environment.logOperation("Caching initial entries...");
		int entryCountAdded = 0;
		int entryCountTotal = newRecords.getRecord().size();
		
		// First, we will create the list of all of these records.
		List<RawElementData> workingRecordList = newRecords.getRecord().stream().map((elementRawCid) -> {
			IpfsFile cid = IpfsFile.fromIpfsCid(elementRawCid);
			RawElementData data = new RawElementData();
			data.elementRawCid = elementRawCid;
			data.elementCid = cid;
			return data;
		}).collect(Collectors.toList());
		
		// Queue up the async size checks and make sure that they are ok before we proceed.
		CommandHelpers.queueAndProcessElementRecordSize(scheduler, workingRecordList);
		
		// Fetch the StreamRecord instances so we can check the sizes of the elements inside.
		CommandHelpers.pinAndLoadElementRecords(scheduler, cache, _publicKey, workingRecordList);
		
		// Now, cache all the element meta-data entries and find their sizes for consideration into the cache.
		List<CacheAlgorithm.Candidate<RawElementData>> candidatesList = CommandHelpers.fetchLeafSizes(scheduler, videoEdgePixelMax, workingRecordList);
		
		if (!candidatesList.isEmpty())
		{
			long currentTimeMillis = System.currentTimeMillis();
			CacheAlgorithm.Candidate<RawElementData> firstElement = candidatesList.remove(0);
			
			// Make sure that we will have enough space to cache this new channel (we pro-actively clear some of the cache to make some space when adding a new followee).
			// We want to make sure that this will minimally hold the most recent entry.
			long maxCacheFullBytes = prefs.followCacheTargetBytes() - firstElement.byteSize();
			long currentCacheSizeBytes = CacheHelpers.getCurrentCacheSizeBytes(followIndex);
			long reducedCacheSizeBytes = Math.min(maxCacheFullBytes, CacheHelpers.getTargetCacheSizeBeforeNewChannel(prefs));
			CacheAlgorithm pruningAlgorithm = new CacheAlgorithm(reducedCacheSizeBytes, currentCacheSizeBytes);
			CacheHelpers.pruneCacheIfNeeded(cache, followIndex, pruningAlgorithm, _publicKey, 0L);
			
			// Start the pin operations before updating the cache accounting (since we don't want to lock-step the load).
			// Note that we need to resolve the first entry, before the others, in order to correct the cache size.
			// This could be handled in a different way, if we assume that the pin of the first element will succeed, since we already checked its size.
			RawElementData firstRawData = firstElement.data();
			// Pin the leaves.
			CommandHelpers.pinLeaves(cache, _publicKey, firstRawData);
			
			// Note that we need to resolve the first entry, before the others, in order to correct the cache size.
			// This could be handled in a different way, if we assume that the pin of the first element will succeed, since we already checked its size.
			CommandHelpers.processLeaves(environment, scheduler, followIndex, _publicKey, fetchedRoot, currentTimeMillis, firstRawData);
			entryCountAdded += 1;
			
			// Finally, run the cache algorithm for bulk adding and then cache whatever we are told to add.
			CacheAlgorithm algorithm = new CacheAlgorithm(prefs.followCacheTargetBytes(), CacheHelpers.getCurrentCacheSizeBytes(followIndex));
			List<CacheAlgorithm.Candidate<RawElementData>> toAdd = algorithm.toAddInNewAddition(candidatesList);
			
			// Switch to our candidate subset of the list and request they pin all their leaves.
			List<RawElementData> candidateWorkingList = toAdd.stream().map((CacheAlgorithm.Candidate<RawElementData> candidate) -> candidate.data()).collect(Collectors.toList());
			candidateWorkingList.forEach((RawElementData data) -> CommandHelpers.pinLeaves(cache, _publicKey, data));
			entryCountAdded += candidateWorkingList.size();
			
			// Now that all the requests are in-flight, we can start accounting for them as they arrive.
			for (RawElementData data : candidateWorkingList)
			{
				CommandHelpers.processLeaves(environment, scheduler, followIndex, _publicKey, fetchedRoot, currentTimeMillis, data);
			}
		}
		log.finish("Completed initial cache (" + entryCountAdded + " of " + entryCountTotal + " entries cached)");
	}
}
