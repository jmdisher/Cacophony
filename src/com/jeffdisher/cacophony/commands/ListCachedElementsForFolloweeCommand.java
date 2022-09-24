package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _followeeKey);
		
		LocalConfig local = environment.loadExistingConfig();
		
		// Read the data elements.
		LocalIndex localIndex = null;
		FollowIndex followIndex = null;
		GlobalPinCache pinCache = null;
		try (IReadOnlyLocalData localData = local.getSharedLocalData().openForRead())
		{
			localIndex = localData.readLocalIndex();
			followIndex = localData.readFollowIndex();
			pinCache = localData.readGlobalPinCache();
		}
		INetworkScheduler scheduler = environment.getSharedScheduler(local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(scheduler, pinCache, local.getSharedConnection());
		FollowRecord record = followIndex.peekRecord(_followeeKey);
		if (null != record)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			Map<IpfsFile, FollowingCacheElement> cachedMapByElementCid = CacheHelpers.createCachedMap(record);
			IpfsFile root = record.lastFetchedRoot();
			
			StreamIndex index = checker.loadCached(root, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
			StreamRecords records = checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data)).get();
			List<String> recordList = records.getRecord();
			environment.logToConsole("Followee has " + recordList.size() + " elements");
			for(String elementCid : recordList)
			{
				FollowingCacheElement element = cachedMapByElementCid.get(IpfsFile.fromIpfsCid(elementCid));
				String suffix = (null == element)
						? "(not cached)"
						: "(image: " + element.imageHash().toSafeString() + ", leaf: " + element.leafHash().toSafeString() + ")"
				;
				environment.logToConsole("Element CID: " + elementCid + " " + suffix);
			}
		}
		else
		{
			throw new UsageException("Not following " + _followeeKey.toPublicKey());
		}
	}
}
