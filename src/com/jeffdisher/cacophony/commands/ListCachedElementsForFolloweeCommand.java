package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.data.local.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.CacheHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local.loadGlobalPinCache(), local.getSharedConnection());
		FollowIndex followIndex = local.loadFollowIndex();
		FollowRecord record = followIndex.getFollowerRecord(_followeeKey);
		if (null != record)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			Map<IpfsFile, FollowingCacheElement> cachedMapByElementCid = CacheHelpers.createCachedMap(record);
			IpfsFile root = record.lastFetchedRoot();
			
			byte[] rawIndex = checker.loadCached(root);
			StreamIndex index = GlobalData.deserializeIndex(rawIndex);
			StreamRecords records = GlobalData.deserializeRecords(checker.loadCached(IpfsFile.fromIpfsCid(index.getRecords())));
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
		local.writeBackConfig();
	}
}
